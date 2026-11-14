package forge.ai;

import forge.card.MagicColor;
import forge.card.mana.ManaCost;
import forge.card.mana.ManaCostShard;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.ability.AbilityUtils;
import forge.game.card.CardCollectionView;
import forge.game.keyword.Keyword;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 21, an untapped land is preferred over a tapped one only if it brings the colors of a spell
 * it makes castable this turn. Stock AiController.chooseBestLandToPlay counted mana, not colors: with Plains,
 * Jungle Shrine and Birds of Paradise in hand it played Plains on turn 1 "to cast Birds", then Plains again
 * "for Nature's Lore", and the tri-lands came down tapped on turns 3 and 4 (Tricolore, 3 colors).
 */
public final class TapLandColorAi {
    private TapLandColorAi() { }

    /**
     * Rule 66: the mana we can really make this turn, for the land choice. Stock getAvailableManaEstimate counts a
     * costed mana ability at its net (Castle Garenbrig's {2}{G}{G},{T}: six G = 2) even when the other sources cannot
     * pay it: with Castle Garenbrig and a Forest, Yorvo (GGG) looked castable already, so Wid played Mosswort Bridge
     * tapped instead of the Forest (a tester report 29/10). Here a costed ability adds only if the other free
     * sources pay its whole cost (stock convertAmount() read {2}{G}{G} as 1), and at most once.
     */
    public static int landPlayManaEstimate(final Player ai) {
        int free = 0;
        int bestGain = 0;
        final java.util.List<int[]> costed = new java.util.ArrayList<>(); // {free of the source, cost, produced}
        for (final Card src : ai.getCardsIn(ZoneType.Battlefield)) {
            int srcFree = 0;
            for (final SpellAbility ma : src.getManaAbilities()) {
                ma.setActivatingPlayer(ai);
                if (!ma.canPlay()) {
                    continue;
                }
                // the whole mana cost: CostPartMana.convertAmount() is 1 for {2}{G}{G}
                final int cost = ma.getPayCosts().getCostMana() != null ? ma.getPayCosts().getCostMana().getMana().getCMC() : 0;
                // magic-tools: rule 105, "Combo R W" (Boros Guildgate) is one mana, not three
                final int produced = producedCount(ma.getParamOrDefault("Produced", ""))
                        * AbilityUtils.calculateAmount(src, ma.getParamOrDefault("Amount", "1"), ma);
                if (cost == 0) {
                    srcFree = Math.max(srcFree, produced);
                } else if (produced > cost) {
                    costed.add(new int[] {-1, cost, produced});
                }
            }
            for (final int[] c : costed) {
                if (c[0] < 0) {
                    c[0] = srcFree;
                }
            }
            free += srcFree;
        }
        for (final int[] c : costed) {
            if (free - c[0] >= c[1]) {
                bestGain = Math.max(bestGain, c[2] - c[1] - c[0]);
            }
        }
        return free + bestGain + ai.getManaPool().totalMana();
    }

    /**
     * Rule 105: the mana symbols one activation adds. "R G" is two, but "Combo R W" (a choice of color: Guildgates,
     * temples, painlands) and "Any" are one. Rule 66 split the parameter on spaces and read two Boros Guildgates as
     * six mana: with nothing "newly castable" at six, rule 69 played Temple of Triumph tapped instead of the Mountain
     * that cast Orcish Cannoneers (a tester report 30/10 10:31, Ork - Profanation turn 6).
     */
    static int producedCount(final String produced) {
        final String p = produced.trim();
        if (p.isEmpty()) {
            return 1;
        }
        if (p.startsWith("Combo") || p.startsWith("Any") || p.startsWith("Chosen") || p.startsWith("Special")) {
            return 1;
        }
        return p.split("\\s+").length;
    }

    /** The untapped lands that make a spell in hand castable this turn, colors included. */
    public static CardCollection usefulUntappedLands(final Player ai, final CardCollectionView nonTappedLands,
            final CardCollectionView nonLandsInHand, final int manaAvailable) {
        byte pool = 0;
        for (final Card source : ComputerUtilMana.getAvailableManaSources(ai, true)) {
            pool |= colorMask(source);
        }
        final CardCollection useful = new CardCollection();
        for (final Card land : nonTappedLands) {
            final int inc = land.getMaxManaProduced();
            if (inc <= 0) {
                continue;
            }
            final byte colors = (byte) (pool | colorMask(land));
            for (final Card c : nonLandsInHand) {
                if (idleCard(ai, c)) {
                    continue;
                }
                final ManaCost cost = c.getManaCost();
                final int cmc = cost.getCMC();
                // same test as stock: castable with this land and not before, or an X spell to pump
                final boolean newlyCastable = (cmc - manaAvailable) * (cmc - manaAvailable - inc - 1) < 0
                        || (cost.countX() > 0 && cmc >= manaAvailable);
                // magic-tools: rule 205, each colored symbol needs its own source (see payableWithSources)
                if (newlyCastable && payableWith(cost, colors) && payableWithSources(cost, sourceMasks(ai, land))
                        || foretellsWith(c, manaAvailable, inc)) {
                    useful.add(land);
                    break;
                }
            }
        }
        return useful;
    }

    /**
     * Rule 89: a card with foretell in hand turns {2} of generic mana into a play this turn (exile it face down,
     * cast it later for its foretell cost). Stock and rules 21/69 only compared mana values of the spells: with one
     * Swamp in play, Snowboard played Shimmerdrift Vale tapped and could not foretell Vengeful Reaper (a tester report
     * 29/10 12:26, ElfBall - Snowboard turn 3); the untapped Island would have.
     */
    public static boolean foretellsWith(final Card c, final int manaAvailable, final int inc) {
        return inc > 0 && manaAvailable < 2 && manaAvailable + inc >= 2 && c.hasKeyword(Keyword.FORETELL);
    }

    /** Rule 69: an Equipment with no creature of ours to carry it (Prosthetic Injector on turn 1) can wait. */
    static boolean equipsNothing(final Player ai, final Card c) {
        return c.isEquipment() && ai.getCreaturesInPlay().isEmpty();
    }

    /**
     * Rule 69: when the untapped land makes nothing new castable, play the slow land first: one that enters tapped,
     * or one whose arrival puts a land onto the battlefield tapped (Obscura Storefront). Stock only skipped the
     * taplands when a spell needed the mana and otherwise let the land score decide: Toxico played Mirrex on turn 1
     * "for Prosthetic Injector" (no creature to equip) and kept Obscura Storefront, which brings the colors later
     * (a tester report 29/10). Two cheap spells that fit together only with the extra land still count as a use.
     */
    public static CardCollection slowLandsIfIdle(final Player ai, final CardCollectionView landList,
            final CardCollectionView nonTappedLands, final CardCollectionView nonLandsInHand, final int manaAvailable) {
        final CardCollection slow = new CardCollection();
        int inc = 0;
        for (final Card land : landList) {
            if (!nonTappedLands.contains(land) || fetchesTapped(land)) {
                slow.add(land);
            } else {
                inc = Math.max(inc, land.getMaxManaProduced());
            }
        }
        if (slow.isEmpty() || slow.size() == landList.size() || inc <= 0) {
            return new CardCollection();
        }
        final java.util.List<Integer> costs = new java.util.ArrayList<>();
        for (final Card c : nonLandsInHand) {
            if (idleCard(ai, c)) {
                continue;
            }
            final ManaCost cost = c.getManaCost();
            if (cost.countX() > 0 && cost.getCMC() >= manaAvailable) {
                return new CardCollection(); // an X spell grows with the land
            }
            if (foretellsWith(c, manaAvailable, inc)) {
                return new CardCollection(); // rule 89: the untapped land pays the {2} to foretell
            }
            costs.add(cost.getCMC());
        }
        java.util.Collections.sort(costs);
        if (NewAi.on(ai, 271)) {
            return untappedColorUse(ai, landList, nonTappedLands, nonLandsInHand, manaAvailable) ? new CardCollection() : slow;
        }
        // magic-tools: rule 250, or more mana spent this turn (Guardmage + Talisman at 6 instead of Prince + Talisman at 5)
        return castCount(costs, manaAvailable + inc) > castCount(costs, manaAvailable)
                || maxSpend(costs, manaAvailable + inc) > maxSpend(costs, manaAvailable) ? new CardCollection() : slow;
    }

    /**
     * Rule 271 (a tester report 09/11 12:12, a test game turn 1): with Swamp and Obscura Storefront,
     * Ruin Crab and Jace's Phantasm ({U} each) counted as "castable with the Swamp" because rule 69 only compared mana
     * values. The Swamp came down, the Storefront waited, and Mesmeric Orb missed turn 2. A use of an untapped land now
     * needs the colors: only the spells payable with our sources plus that land count.
     */
    static boolean untappedColorUse(final Player ai, final CardCollectionView landList,
            final CardCollectionView nonTappedLands, final CardCollectionView nonLandsInHand, final int manaAvailable) {
        byte pool = 0;
        for (final Card source : ComputerUtilMana.getAvailableManaSources(ai, true)) {
            pool |= colorMask(source);
        }
        final java.util.List<Integer> costsNow = new java.util.ArrayList<>();
        for (final Card c : nonLandsInHand) {
            if (!idleCard(ai, c) && payableWith(c.getManaCost(), pool)) {
                costsNow.add(c.getManaCost().getCMC());
            }
        }
        java.util.Collections.sort(costsNow);
        for (final Card land : landList) {
            if (!nonTappedLands.contains(land) || fetchesTapped(land)) {
                continue;
            }
            final int inc = land.getMaxManaProduced();
            if (inc <= 0) {
                continue;
            }
            final byte colors = (byte) (pool | colorMask(land));
            final java.util.List<Integer> costs = new java.util.ArrayList<>();
            for (final Card c : nonLandsInHand) {
                if (!idleCard(ai, c) && payableWith(c.getManaCost(), colors)) {
                    costs.add(c.getManaCost().getCMC());
                }
            }
            java.util.Collections.sort(costs);
            if (castCount(costs, manaAvailable + inc) > castCount(costsNow, manaAvailable)
                    || maxSpend(costs, manaAvailable + inc) > maxSpend(costsNow, manaAvailable)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Rule 250: the most mana the spells in hand can use together within a budget (subset sum). Rule 69 compared the
     * number of spells castable cheapest first: with Charming Prince and a Talisman (2 each), Elite Guardmage and Brago
     * (4 each) and five mana, the untapped Plains made six but still "two spells", so Bad River came down tapped and
     * Guardmage waited (a tester report 08/11, D143, a test game turn 7).
     */
    static int maxSpend(final java.util.List<Integer> costs, final int budget) {
        if (budget <= 0) {
            return 0;
        }
        final boolean[] reach = new boolean[budget + 1];
        reach[0] = true;
        for (final int c : costs) {
            if (c <= 0 || c > budget) {
                continue;
            }
            for (int m = budget; m >= c; m--) {
                if (reach[m - c]) {
                    reach[m] = true;
                }
            }
        }
        for (int m = budget; m > 0; m--) {
            if (reach[m]) {
                return m;
            }
        }
        return 0;
    }

    /** Rule 250: the untapped lands whose extra mana raises what the hand can spend this turn. */
    public static CardCollection untappedForSpend(final Player ai, final CardCollectionView nonTappedLands,
            final CardCollectionView nonLandsInHand, final int manaAvailable) {
        byte pool = 0;
        for (final Card source : ComputerUtilMana.getAvailableManaSources(ai, true)) {
            pool |= colorMask(source);
        }
        final java.util.List<Integer> costsNow = new java.util.ArrayList<>();
        for (final Card c : nonLandsInHand) {
            if (!idleCard(ai, c) && c.getManaCost().countX() == 0 && payableWith(c.getManaCost(), pool)) {
                costsNow.add(c.getManaCost().getCMC());
            }
        }
        final int now = maxSpend(costsNow, manaAvailable);
        final CardCollection spent = new CardCollection();
        for (final Card land : nonTappedLands) {
            final int inc = land.getMaxManaProduced();
            if (inc <= 0) {
                continue;
            }
            // only the spells the colors with this land pay
            final byte colors = (byte) (pool | colorMask(land));
            final java.util.List<Integer> costs = new java.util.ArrayList<>();
            for (final Card c : nonLandsInHand) {
                if (!idleCard(ai, c) && c.getManaCost().countX() == 0 && payableWith(c.getManaCost(), colors)) {
                    costs.add(c.getManaCost().getCMC());
                }
            }
            if (maxSpend(costs, manaAvailable + inc) > now) {
                spent.add(land);
            }
        }
        return spent;
    }

    /**
     * Rule 250: a card that cannot be cast this turn whatever the mana: rules 65 and 69, plus a spell whose every
     * targeted spell ability has no legal target (an Aura with no creature on the battlefield). Turn 1 with Curiosity,
     * Island and Halimar Depths, stock counted Curiosity as "castable with the Island" and played it, Halimar Depths
     * then came down tapped on turn 2 (a tester report 08/11, D143, a test game).
     */
    public static boolean idleCard(final Player ai, final Card c) {
        return protectsNothing(ai, c) || equipsNothing(ai, c) || targetsNothing(ai, c);
    }

    static boolean targetsNothing(final Player ai, final Card c) {
        if (c.getSpellAbilities().isEmpty()) {
            return false;
        }
        for (final SpellAbility sa : c.getSpellAbilities()) {
            if (!sa.isSpell() || !sa.usesTargeting()) {
                return false;
            }
            sa.setActivatingPlayer(ai);
            if (sa.getTargetRestrictions().getMinTargets(c, sa) == 0 || sa.getTargetRestrictions().hasCandidates(sa)) {
                return false;
            }
        }
        return true;
    }

    private static int castCount(final java.util.List<Integer> sortedCosts, final int mana) {
        int left = mana;
        int n = 0;
        for (final int c : sortedCosts) {
            if (c > left) {
                break;
            }
            left -= c;
            n++;
        }
        return n;
    }

    /** A land sacrificed or not on arrival that puts a land from the library onto the battlefield tapped. */
    static boolean fetchesTapped(final Card land) {
        if (land.getMaxManaProduced() > 0) {
            return false;
        }
        boolean selfEtb = false;
        for (final forge.game.trigger.Trigger t : land.getTriggers()) {
            if (t.getMode() == forge.game.trigger.TriggerType.ChangesZone
                    && "Battlefield".equals(t.getParam("Destination")) && "Card.Self".equals(t.getParam("ValidCard"))) {
                selfEtb = true;
            }
        }
        if (!selfEtb) {
            return false;
        }
        for (final java.util.Map.Entry<String, String> sv : land.getSVars().entrySet()) {
            final String v = sv.getValue();
            if (v.contains("ChangeZone") && v.contains("Origin$ Library") && v.contains("Destination$ Battlefield")
                    && v.contains("Tapped$ True") && (v.contains("Land") || v.contains(".Basic"))) {
                return true;
            }
        }
        return false;
    }

    /** Rule 65: a pump or protection spell for our side (Heroic Intervention, Blossoming Defense) with no creature of ours. */
    static boolean protectsNothing(final Player ai, final Card c) {
        if (c.isPermanent() || c.getSpellAbilities().isEmpty()
                || !ai.getCreaturesInPlay().isEmpty()) {
            return false;
        }
        for (final SpellAbility sa : c.getSpellAbilities()) {
            final ApiType api = sa.getApi();
            if (api != ApiType.Pump && api != ApiType.PumpAll && api != ApiType.Protection && api != ApiType.ProtectionAll) {
                return false;
            }
            if (sa.getParamOrDefault("NumAtt", "").startsWith("-") || sa.getParamOrDefault("NumDef", "").startsWith("-")) {
                return false; // -X/-X: removal
            }
            final String valid = sa.getParamOrDefault("ValidCards", sa.getParamOrDefault("ValidTgts", ""));
            if (!valid.contains("YouCtrl") && (api == ApiType.PumpAll || api == ApiType.ProtectionAll || !valid.startsWith("Creature"))) {
                return false;
            }
            if (sa.getSubAbility() != null) {
                return false;
            }
        }
        return true;
    }

    static boolean payableWith(final ManaCost cost, final byte colors) {
        for (final ManaCostShard shard : cost) {
            final byte need = shard.getColorMask();
            if (need == 0 || shard.isPhyrexian() || shard.isOr2Generic()) {
                continue;
            }
            if ((need & colors) == 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Rule 205: {2}{B/R}{B/R} with one Swamp is not payable, however much green mana comes with it. payableWith only
     * looks at which colors exist: Pod played a Forest "for Murderous Redcap" with Overgrown Tomb in hand, then spent
     * its mana on a second sacrifice outlet (a tester report 5/11, a test game). One source per colored symbol.
     */
    static boolean payableWithSources(final ManaCost cost, final java.util.List<Byte> sources) {
        final java.util.List<Byte> needs = new java.util.ArrayList<>();
        for (final ManaCostShard shard : cost) {
            final byte need = shard.getColorMask();
            if (need != 0 && !shard.isPhyrexian() && !shard.isOr2Generic()) {
                needs.add(need);
            }
        }
        final int[] usedBy = new int[sources.size()];
        java.util.Arrays.fill(usedBy, -1);
        for (int i = 0; i < needs.size(); i++) {
            if (!assignSource(i, needs, sources, usedBy, new boolean[sources.size()])) {
                return false;
            }
        }
        return true;
    }

    private static boolean assignSource(final int need, final java.util.List<Byte> needs,
            final java.util.List<Byte> sources, final int[] usedBy, final boolean[] seen) {
        for (int s = 0; s < sources.size(); s++) {
            if (seen[s] || (needs.get(need) & sources.get(s)) == 0) {
                continue;
            }
            seen[s] = true;
            if (usedBy[s] < 0 || assignSource(usedBy[s], needs, sources, usedBy, seen)) {
                usedBy[s] = need;
                return true;
            }
        }
        return false;
    }

    /** The colors of each mana we can make this turn, land optionally added; a source of two counts twice. */
    static java.util.List<Byte> sourceMasks(final Player ai, final Card land) {
        final java.util.List<Byte> masks = new java.util.ArrayList<>();
        for (final Card source : ComputerUtilMana.getAvailableManaSources(ai, true)) {
            addSource(masks, source);
        }
        if (land != null) {
            addSource(masks, land);
        }
        return masks;
    }

    /** Rule 215: the same at our next turn, tapped sources included. */
    static java.util.List<Byte> allSourceMasks(final Player ai, final Card land) {
        final java.util.List<Byte> masks = new java.util.ArrayList<>();
        for (final Card source : ai.getCardsIn(ZoneType.Battlefield)) {
            if (!source.getManaAbilities().isEmpty()) {
                addSource(masks, source);
            }
        }
        if (land != null) {
            addSource(masks, land);
        }
        return masks;
    }

    private static void addSource(final java.util.List<Byte> masks, final Card source) {
        final int n = Math.max(1, source.getMaxManaProduced());
        for (int i = 0; i < n; i++) {
            masks.add(colorMask(source));
        }
    }

    /** Rule 205: the highest mana value in hand we would really cast with these sources, or -1. */
    private static int bestCastable(final Player ai, final CardCollectionView nonLandsInHand,
            final java.util.List<Byte> sources) {
        int best = -1;
        for (final Card c : nonLandsInHand) {
            final ManaCost cost = c.getManaCost();
            final int cmc = cost.getCMC();
            if (cmc > best && cmc <= sources.size() && cost.countX() == 0 && payableWithSources(cost, sources)
                    && !protectsNothing(ai, c) && !equipsNothing(ai, c)
                    && ShockLandAi.hasReasonToPlayCardThisTurn(ai, c)) {
                best = cmc;
            }
        }
        return best;
    }

    /**
     * Rule 205: the land that enters untapped for life (shockland) is worth its life when it casts something bigger
     * than the best untapped land would. Stock sets it aside as a tapped land and only pays when a spell costs
     * exactly one more than the lands in play, so mana creatures and color needs were never seen.
     */
    public static boolean shockUnlocks(final Player ai, final Card shock, final CardCollectionView otherLands,
            final CardCollectionView nonLandsInHand) {
        int without = bestCastable(ai, nonLandsInHand, sourceMasks(ai, null));
        for (final Card other : otherLands) {
            without = Math.max(without, bestCastable(ai, nonLandsInHand, sourceMasks(ai, other)));
        }
        return bestCastable(ai, nonLandsInHand, sourceMasks(ai, shock)) > without;
    }

    /** Rule 205: "as this enters, you may pay N life; if you don't, it enters tapped". */
    public static boolean paysLifeForUntapped(final SpellAbility reSA) {
        return reSA.hasParam("UnlessCost") && reSA.getParam("UnlessCost").startsWith("PayLife");
    }

    private static byte colorMask(final Card c) {
        byte mask = 0;
        for (final String color : c.getProducibleColors()) {
            mask |= MagicColor.fromName(color);
        }
        return mask;
    }
}
