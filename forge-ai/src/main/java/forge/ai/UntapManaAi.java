package forge.ai;

import java.util.Map;
import com.google.common.collect.MapMaker;

import forge.card.MagicColor;
import forge.card.mana.ManaCost;
import forge.card.mana.ManaCostShard;
import forge.game.ability.AbilityUtils;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.cost.Cost;
import forge.game.cost.CostPart;
import forge.game.cost.CostTap;
import forge.game.phase.PhaseHandler;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.AbilityManaPart;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 146, a free land untapper is mana (combo relue « vraie » Aggravated Assault + Krosan Restorer + Lotus
 * Field). "{T}: Untap target land" on a land that gives N mana is N more mana: tap the land, untap it, tap it again. Forge's
 * payment code never counts it, so with Lotus Field (3) and the Restorer the AI never paid Aggravated Assault (5) and its
 * endless combats. Here, in its own main phase with an empty stack, when something it wants (a spell in hand, an ability on
 * the battlefield) costs more than its mana but no more than its mana plus what the land gives, the AI taps the land for the
 * color that cost needs (the mana floats), then the untapper targets that land; the payment then uses the pool and the land.
 * Generic: any untapper whose only cost is {T}, any land with a mana ability without mana cost.
 */
public final class UntapManaAi {
    private UntapManaAi() { }

    /** land floated for an untapper, with turn and phase */
    private static final Map<Player, Object[]> PENDING = new MapMaker().weakKeys().makeMap(); // magic-tools: identity keys (players of two games share ids), thread-safe

    static boolean phaseOk(final Player ai) {
        final PhaseHandler ph = ai.getGame().getPhaseHandler();
        return ph.isPlayerTurn(ai) && ph.getPhase().isMain() && ai.getGame().getStack().isEmpty();
    }

    /** "{T}: untap target land" with no other cost. */
    static boolean isLandUntapper(final SpellAbility sa) {
        if (sa == null || sa.getApi() != ApiType.Untap || !sa.isActivatedAbility() || !sa.usesTargeting()
                || sa.isManaAbility()) {
            return false;
        }
        final Cost cost = sa.getPayCosts();
        if (cost == null || !cost.hasTapCost()) {
            return false;
        }
        for (final CostPart part : cost.getCostParts()) {
            if (!(part instanceof CostTap)) {
                return false;
            }
        }
        final String tgts = sa.getParam("ValidTgts");
        // magic-tools: rule 207, a basic type is a land too (Arbor Elf: « target Forest »)
        return tgts != null && (tgts.contains("Land") || tgts.contains("Forest") || tgts.contains("Plains")
                || tgts.contains("Island") || tgts.contains("Swamp") || tgts.contains("Mountain"));
    }

    /** magic-tools: rule 207, a creature ready to untap a land of ours is worth that land's mana (Arbor Elf + Forest) */
    public static boolean isReadyUntapper(final Player ai, final Card c) {
        if (c == null || c.isTapped() || c.isLand()) {
            return false;
        }
        for (final SpellAbility u : c.getSpellAbilities()) {
            if (isLandUntapper(u)) {
                u.setActivatingPlayer(ai);
                if (u.canPlay() && !bestLands(ai, u, false).isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    /** magic-tools: rule 207, called by UntapAi for an AILogic that would refuse (PoolExtraMana waits for main 2): no side
     *  effect, chooseLands answers right after. */
    public static boolean hasManaPlan(final Player ai, final SpellAbility sa) {
        return pendingLand(ai, sa) != null || tappedLandsPlan(ai, sa) != null;
    }

    private static java.util.List<Card> tappedLandsPlan(final Player ai, final SpellAbility sa) {
        if (!isLandUntapper(sa) || !phaseOk(ai)) {
            return null;
        }
        final java.util.List<Card> lands = bestLands(ai, sa, true);
        int gain = 0;
        for (final Card l : lands) {
            gain += landMana(l);
        }
        if (gain < 1) {
            return null;
        }
        // the estimate counts the engines the payment refuses (rule 196): Birchlore would not tap this untapper
        final int avail = ComputerUtilMana.getAvailableManaEstimate(ai) + ai.getManaPool().totalMana() - TapEngineAi.refusedMana(ai);
        return wantsInWindow(ai, avail, gain) ? lands : null;
    }

    /** a ready untapper able to target this land, or null */
    static SpellAbility untapperFor(final Player ai, final Card land) {
        for (final Card c : ai.getCardsIn(ZoneType.Battlefield)) {
            if (c == land || c.isTapped()) {
                continue;
            }
            for (final SpellAbility u : c.getSpellAbilities()) {
                if (!isLandUntapper(u)) {
                    continue;
                }
                u.setActivatingPlayer(ai);
                if (u.canPlay() && u.canTarget(land)) {
                    return u;
                }
            }
        }
        return null;
    }

    /** plain land mana ability: {T} only. */
    static boolean isPlainLandMana(final SpellAbility sa) {
        if (sa == null || sa.getApi() != ApiType.Mana || !sa.isManaAbility() || sa.hasParam("AILogic")) {
            return false;
        }
        final Card host = sa.getHostCard();
        final Cost cost = sa.getPayCosts();
        return host != null && host.isLand() && cost != null && cost.hasTapCost() && cost.hasNoManaCost()
                && cost.getCostParts().size() == 1;
    }

    static int produced(final SpellAbility sa) {
        final String p = sa.getParamOrDefault("Produced", "C");
        int n = p.startsWith("Combo") || "Any".equals(p) ? 1 : p.split(" ").length;
        if (sa.hasParam("Amount")) {
            n *= AbilityUtils.calculateAmount(sa.getHostCard(), sa.getParam("Amount"), sa);
        }
        return n;
    }

    /** Called by ManaAi's phase check: a plain land mana ability, own main phase, a ready untapper. */
    public static boolean phaseFits(final Player ai, final SpellAbility sa) {
        return isPlainLandMana(sa) && phaseOk(ai) && !sa.getHostCard().isTapped()
                && untapperFor(ai, sa.getHostCard()) != null;
    }

    /** Called by ManaAi for a land's mana ability: float its mana when an untapper turns it into the missing mana. */
    public static boolean shouldFloat(final Player ai, final SpellAbility sa) {
        if (!isPlainLandMana(sa) || !phaseOk(ai)) {
            return false;
        }
        final Card land = sa.getHostCard();
        if (land.isTapped() || untapperFor(ai, land) == null) {
            return false;
        }
        final int gain = produced(sa);
        if (gain < 1) {
            return false;
        }
        final int avail = ComputerUtilMana.getAvailableManaEstimate(ai) + ai.getManaPool().totalMana() - TapEngineAi.refusedMana(ai);
        final AbilityManaPart part = sa.getManaPart();
        final AiController aic = ((PlayerControllerAi) ai.getController()).getAi();
        for (final Card c : ai.getCardsIn(ZoneType.Battlefield)) {
            for (final SpellAbility want : c.getSpellAbilities()) {
                if (!want.isActivatedAbility() || want.isManaAbility() || isLandUntapper(want)) {
                    continue;
                }
                if (tryWant(ai, aic, want, avail, gain, part)) {
                    remember(ai, land);
                    return true;
                }
            }
        }
        for (final Card c : ai.getCardsIn(ZoneType.Hand)) {
            if (c.isLand() || c.isInstant()) {
                continue;
            }
            for (final SpellAbility want : c.getSpells()) {
                if (c.isPermanent() && ai.getGame().getPhaseHandler().is(PhaseType.MAIN1, ai)) {
                    want.setActivatingPlayer(ai);
                    if (!ComputerUtil.castPermanentInMain1(ai, want)) {
                        continue;
                    }
                }
                if (tryWant(ai, aic, want, avail, gain, part)) {
                    remember(ai, land);
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean tryWant(final Player ai, final AiController aic, final SpellAbility want, final int avail,
            final int gain, final AbilityManaPart part) {
        want.setActivatingPlayer(ai);
        if (want.getPayCosts() == null || !want.getPayCosts().hasManaCost()) {
            return false;
        }
        final ManaCost cost = want.getPayCosts().getTotalMana();
        if (cost.countX() > 0) {
            return false;
        }
        final int cmc = cost.getCMC();
        if (cmc <= avail || cmc > avail + gain) {
            return false;
        }
        if (part != null && !part.meetsManaRestrictions(want)) {
            return false;
        }
        if (!want.isSpell() && !want.canPlay()) {
            return false;
        }
        final SpellAbility noCost = want.copyWithNoManaCost();
        if (noCost == null) {
            return false;
        }
        noCost.setActivatingPlayer(ai);
        if (aic.canPlaySa(noCost) != AiPlayDecision.WillPlay) {
            return false;
        }
        if (part != null && (part.isAnyMana() || part.isComboMana())) {
            part.setExpressChoice(colorFor(cost));
        }
        return true;
    }

    /** the color of the cost's first colored shard (all the land's mana in one color pays the most shards) */
    private static String colorFor(final ManaCost cost) {
        for (final ManaCostShard shard : cost) {
            for (final byte c : MagicColor.WUBRG) {
                if ((shard.getColorMask() & c) != 0) {
                    return MagicColor.toShortString(c);
                }
            }
        }
        return "C";
    }

    /** max targets of the untapper (Krosan Restorer with threshold: 3) */
    static int maxTargets(final SpellAbility u) {
        return Math.max(1, u.getTargetRestrictions().getMaxTargets(u.getHostCard(), u));
    }

    /** best mana of one land (plain {T} ability), 0 if none */
    static int landMana(final Card land) {
        int best = 0;
        for (final SpellAbility m : land.getManaAbilities()) {
            if (isPlainLandMana(m)) {
                best = Math.max(best, produced(m));
            }
        }
        // magic-tools: rule 308, a land enchanted by mana auras (Wild Growth) gives one more per aura
        if (best > 0 && NewAi.on(land.getController(), 308)) {
            best += ManaAura301Ai.bonusAuras(land);
        }
        return best;
    }

    /** the untapper's best land targets, most mana first, at most its max targets */
    static java.util.List<Card> bestLands(final Player ai, final SpellAbility u, final boolean tappedOnly) {
        final java.util.List<Card> lands = new java.util.ArrayList<>();
        for (final Card land : ai.getCardsIn(ZoneType.Battlefield)) {
            if (land.isLand() && (!tappedOnly || land.isTapped()) && landMana(land) > 0 && u.canTarget(land)) {
                lands.add(land);
            }
        }
        lands.sort((a, b) -> landMana(b) - landMana(a));
        return lands.subList(0, Math.min(maxTargets(u), lands.size()));
    }

    /** a battlefield ability or a hand spell the AI wants (no-cost copy WillPlay) whose cost lies in (avail, avail+gain] */
    static boolean wantsInWindow(final Player ai, final int avail, final int gain) {
        final AiController aic = ((PlayerControllerAi) ai.getController()).getAi();
        for (final Card c : ai.getCardsIn(ZoneType.Battlefield)) {
            for (final SpellAbility want : c.getSpellAbilities()) {
                if (want.isActivatedAbility() && !want.isManaAbility() && !isLandUntapper(want)
                        && tryWant(ai, aic, want, avail, gain, null)) {
                    return true;
                }
            }
        }
        for (final Card c : ai.getCardsIn(ZoneType.Hand)) {
            if (c.isLand() || c.isInstant()) {
                continue;
            }
            for (final SpellAbility want : c.getSpells()) {
                if (c.isPermanent() && ai.getGame().getPhaseHandler().is(PhaseType.MAIN1, ai)) {
                    want.setActivatingPlayer(ai);
                    if (!ComputerUtil.castPermanentInMain1(ai, want)) {
                        continue;
                    }
                }
                if (tryWant(ai, aic, want, avail, gain, null)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Called by UntapAi: the lands to untap now for mana (the floated land, or the best tapped lands when that mana
     *  reaches a wanted cost), or null to leave the decision to UntapAi. */
    public static java.util.List<Card> chooseLands(final Player ai, final SpellAbility sa) {
        final Card floated = pendingLand(ai, sa);
        if (floated != null) {
            done(ai);
            return java.util.Collections.singletonList(floated);
        }
        return tappedLandsPlan(ai, sa);
    }

    /** Called by UntapAi: a sibling "{T}: untap up to N target lands" on the same host is playable now (Krosan
     *  Restorer with threshold): the one-land version must not spend the {T}. */
    public static boolean declineForSibling(final Player ai, final SpellAbility sa) {
        if (!isLandUntapper(sa)) {
            return false;
        }
        for (final SpellAbility u : sa.getHostCard().getSpellAbilities()) {
            if (u != sa && isLandUntapper(u)) {
                u.setActivatingPlayer(ai);
                if (u.canPlay() && maxTargets(u) > maxTargets(sa)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Called by UntapAi when chooseLands declined: on its own turn, an untapper whose lands alone pay a repeatable
     *  ability on the battlefield (Aggravated Assault) is the loop's engine and waits for the next main phase rather
     *  than untapping lands for nothing (it did right after the Assault, then the next Assault had no mana). */
    public static boolean saveForEngine(final Player ai, final SpellAbility sa) {
        if (!isLandUntapper(sa) || !ai.getGame().getPhaseHandler().isPlayerTurn(ai)) {
            return false;
        }
        int gain = 0;
        for (final Card l : bestLands(ai, sa, false)) {
            gain += landMana(l);
        }
        for (final Card c : ai.getCardsIn(ZoneType.Battlefield)) {
            for (final SpellAbility want : c.getSpellAbilities()) {
                if (want.isActivatedAbility() && !want.isManaAbility() && !isLandUntapper(want)
                        && inWindow(want, -1, gain + 1) && want.getPayCosts().getTotalMana().getCMC() >= 2) {
                    return true;
                }
            }
        }
        return false;
    }

    /** A creature whose "{T}: untap target land" is the missing mana of something wanted this turn does not attack:
     *  tapped by the attack, it cannot untap the land in the next main phase. Cost window only (combat, no canPlaySa). */
    public static boolean keepHome(final Player ai, final Card attacker) {
        if (attacker == null || attacker.getController() != ai || !attacker.isCreature()) {
            return false;
        }
        boolean untapper = false;
        for (final SpellAbility u : attacker.getSpellAbilities()) {
            if (isLandUntapper(u)) {
                untapper = true;
                break;
            }
        }
        if (!untapper) {
            return false;
        }
        int gain = 0;
        for (final SpellAbility u : attacker.getSpellAbilities()) {
            if (isLandUntapper(u)) {
                u.setActivatingPlayer(ai);
                int g = 0;
                for (final Card l : bestLands(ai, u, false)) {
                    g += landMana(l);
                }
                gain = Math.max(gain, g);
            }
        }
        final int avail = ComputerUtilMana.getAvailableManaEstimate(ai, false);
        if (gain == 1) {
            // magic-tools: rule 207, Arbor Elf: home if its one mana casts one more spell of the hand in the next main phase
            int left = ComputerUtilMana.getAvailableManaEstimate(ai) + ai.getManaPool().totalMana() - TapEngineAi.refusedMana(ai);
            final java.util.List<Integer> costs = new java.util.ArrayList<>();
            for (final Card c : ai.getCardsIn(ZoneType.Hand)) {
                if (!c.isLand() && !c.isInstant() && c.getCMC() > 0) {
                    costs.add(c.getCMC());
                }
            }
            java.util.Collections.sort(costs);
            for (final int cost : costs) {
                if (cost > left) {
                    return cost <= left + gain;
                }
                left -= cost;
            }
            return false;
        }
        if (gain < 2) {
            return false;
        }
        for (final Card c : ai.getCardsIn(ZoneType.Battlefield)) {
            for (final SpellAbility want : c.getSpellAbilities()) {
                // a repeatable ability: in reach thanks to the untapper, or paid by the untapper alone (a loop engine)
                if (want.isActivatedAbility() && !want.isManaAbility() && !isLandUntapper(want)
                        && (inWindow(want, avail, gain) || inWindow(want, -1, gain + 1))) {
                    return true;
                }
            }
        }
        for (final Card c : ai.getCardsIn(ZoneType.Hand)) {
            if (c.isLand() || c.isInstant()) {
                continue;
            }
            for (final SpellAbility want : c.getSpells()) {
                if (inWindow(want, avail, gain)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean inWindow(final SpellAbility want, final int avail, final int gain) {
        if (want.getPayCosts() == null || !want.getPayCosts().hasManaCost()) {
            return false;
        }
        final ManaCost cost = want.getPayCosts().getTotalMana();
        return cost.countX() == 0 && cost.getCMC() > avail && cost.getCMC() <= avail + gain;
    }

    private static void remember(final Player ai, final Card land) {
        final PhaseHandler ph = ai.getGame().getPhaseHandler();
        PENDING.put(ai, new Object[] {land, ph.getTurn(), ph.getPhase()});
    }

    /** Called by UntapAi: the land floated for this untapper, still tapped, same turn and phase; null otherwise. */
    public static Card pendingLand(final Player ai, final SpellAbility sa) {
        final Object[] p = PENDING.get(ai);
        if (p == null || !isLandUntapper(sa)) {
            return null;
        }
        final PhaseHandler ph = ai.getGame().getPhaseHandler();
        final Card land = (Card) p[0];
        if (!ph.isPlayerTurn(ai) || (Integer) p[1] != ph.getTurn() || p[2] != ph.getPhase() || !land.isTapped()
                || !land.isInPlay() || !sa.canTarget(land)) {
            PENDING.remove(ai);
            return null;
        }
        return land;
    }

    public static void done(final Player ai) {
        PENDING.remove(ai);
    }
}
