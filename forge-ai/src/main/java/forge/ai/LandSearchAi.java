package forge.ai;

import forge.card.MagicColor;
import forge.card.mana.ManaCostShard;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardLists;
import forge.game.card.CardPredicates;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 199, a search among any lands (Expedition Map, Crop Rotation, Sylvan Scrying) looks at what the
 * land does for this game. Stock ComputerUtilCard.getBestLandAI took a random nonbasic ("TODO improve ranking"):
 * tester 02/11 (a test game), with Glimmerpost in hand the Map should find Cloudpost, which makes more mana.
 * Each land is scored on the mana it will make given the board and the hand, on the colors the hand still lacks,
 * on entering tapped, and a legendary land we already hold is left. Ties go to getBestLandAI as before.
 * Rule 246 (tester 08/11, a test game T2: the Map took an Island over Urza's Mine with Power Plant out):
 * a land of a set (DeckNeeds:Name$ its partners, Tron, Locus...) scores one per partner held and two more when it
 * completes the set, Count$UrzaLands reads the set as if the land were in play, the deck's plan color only counts when
 * nothing we hold makes it yet, and a tie goes to the land with the fewest copies left in the library (a basic comes
 * back by drawing, the tutor fetches the rare piece).
 */
public final class LandSearchAi {
    private LandSearchAi() { }

    /** The land to take among {@code lands}; {@code tappedAnyway}: the effect itself puts it in tapped. */
    public static Card choose(final Player ai, final Iterable<Card> lands, final boolean tappedAnyway) {
        int best = Integer.MIN_VALUE;
        final CardCollection top = new CardCollection();
        for (final Card land : lands) {
            final int s = score(ai, land, tappedAnyway);
            if (s > best) {
                best = s;
                top.clear();
            }
            if (s == best) {
                top.add(land);
            }
        }
        if (top.size() > 1) {
            int fewest = Integer.MAX_VALUE;
            final CardCollection rare = new CardCollection();
            for (final Card land : top) {
                final int left = CardLists.count(ai.getCardsIn(ZoneType.Library), CardPredicates.nameEquals(land.getName()));
                if (left < fewest) {
                    fewest = left;
                    rare.clear();
                }
                if (left == fewest) {
                    rare.add(land);
                }
            }
            return ComputerUtilCard.getBestLandAI(rare);
        }
        return ComputerUtilCard.getBestLandAI(top);
    }

    /** Rule 246: one per partner of the land's set we hold, two more if the land completes it; 0 for a land of no set. */
    static int setBonus(final CardCollection held, final Card land) {
        if (land.getRules() == null || land.getRules().getAiHints() == null) {
            return 0;
        }
        final forge.card.DeckHints needs = land.getRules().getAiHints().getDeckNeeds();
        if (needs == null || !needs.isValid() || !land.isLand()) {
            return 0;
        }
        final java.util.List<String> partners = needs.names(forge.card.DeckHints.Type.NAME);
        if (partners.isEmpty() || held.anyMatch(CardPredicates.nameEquals(land.getName()))) {
            return 0;
        }
        int have = 0;
        for (final String p : partners) {
            if (held.anyMatch(CardPredicates.nameEquals(p))) {
                have++;
            }
        }
        return have == 0 ? 0 : have + (have == partners.size() ? 2 : 0);
    }

    static int score(final Player ai, final Card land, final boolean tappedAnyway) {
        final CardCollection held = new CardCollection(ai.getCardsIn(ZoneType.Battlefield));
        held.addAll(ai.getCardsIn(ZoneType.Hand));
        if (land.getType().isLegendary() && held.anyMatch(CardPredicates.nameEquals(land.getName()))) {
            return -100;
        }
        final CardCollection found = FetchLandPlayAi.fetchable(ai, land);
        final int mana = found.isEmpty() ? futureMana(ai, land) : 1;
        int score = mana == 0 ? -2 : 2 * (mana - 1);
        score += setBonus(held, land);
        if (!tappedAnyway && RemovalReserveAi.entersTapped(ai, land)) {
            score--;
        }
        byte colors = colorsOf(land);
        for (final Card c : found) {
            colors |= colorsOf(c);
        }
        final int[] supply = new int[MagicColor.WUBRG.length];
        for (final Card src : held) {
            final byte mask = colorsOf(src);
            for (int i = 0; i < supply.length; i++) {
                if ((mask & MagicColor.WUBRG[i]) != 0) {
                    supply[i]++;
                }
            }
        }
        // two points per color a card of the hand still lacks, one for the color the deck's plan asks for
        final int[] gap = new int[supply.length];
        for (final Card c : CardLists.filter(ai.getCardsIn(ZoneType.Hand), CardPredicates.NON_LANDS)) {
            final int[] pips = new int[supply.length];
            for (final ManaCostShard shard : c.getManaCost()) {
                for (int i = 0; i < pips.length; i++) {
                    if ((shard.getColorMask() & MagicColor.WUBRG[i]) != 0) {
                        pips[i]++;
                    }
                }
            }
            for (int i = 0; i < pips.length; i++) {
                gap[i] = Math.max(gap[i], pips[i] - supply[i]);
            }
        }
        for (int i = 0; i < gap.length; i++) {
            if (gap[i] > 0 && (colors & MagicColor.WUBRG[i]) != 0) {
                score += 2;
            }
        }
        final String plan = FetchColorAi.neededBasicType(ai, MagicColor.Constant.BASIC_LANDS);
        final int planIdx = plan == null ? -1 : MagicColor.Constant.BASIC_LANDS.indexOf(plan);
        if (planIdx >= 0 && (colors & MagicColor.WUBRG[planIdx]) != 0 && supply[planIdx] == 0) {
            score++;
        }
        return score;
    }

    /** The most mana one tap of the land makes once it is in play with what we hold (Cloudpost counts the Locus). */
    static int futureMana(final Player ai, final Card land) {
        int best = 0;
        for (final SpellAbility sa : land.getManaAbilities()) {
            best = Math.max(best, amount(ai, land, sa));
        }
        return best;
    }

    private static int amount(final Player ai, final Card land, final SpellAbility sa) {
        final String amount = sa.getParamOrDefault("Amount", "1");
        if (amount.matches("\\d+")) {
            return Integer.parseInt(amount);
        }
        final String svar = land.getSVar(amount);
        if (svar.startsWith("Count$Valid ") && !svar.contains("/")) {
            final String valid = svar.substring("Count$Valid ".length());
            final CardCollection seen = new CardCollection(ai.getGame().getCardsIn(ZoneType.Battlefield));
            seen.addAll(ai.getCardsIn(ZoneType.Hand));
            return CardLists.getValidCards(seen, valid, ai, land, sa).size() + (land.isValid(valid, ai, land, sa) ? 1 : 0);
        }
        if (svar.startsWith("Count$UrzaLands.")) {
            // rule 246: the Tron bonus as if the land were in play next to what we hold
            final String[] n = svar.substring("Count$UrzaLands.".length()).split("\\.");
            final CardCollection mine = new CardCollection(ai.getCardsIn(ZoneType.Battlefield));
            mine.addAll(ai.getCardsIn(ZoneType.Hand));
            mine.add(land);
            boolean tron = true;
            for (final String t : new String[] {"Mine", "Power-Plant", "Tower"}) {
                tron &= mine.anyMatch(CardPredicates.isType("Urza's").and(CardPredicates.isType(t)));
            }
            if (n.length == 2 && n[0].matches("\\d+") && n[1].matches("\\d+")) {
                return Integer.parseInt(tron ? n[0] : n[1]);
            }
        }
        return Math.max(1, land.getMaxManaProduced());
    }

    private static byte colorsOf(final Card c) {
        byte mask = 0;
        for (final String color : c.getProducibleColors()) {
            mask |= MagicColor.fromName(color);
        }
        return mask;
    }
}
