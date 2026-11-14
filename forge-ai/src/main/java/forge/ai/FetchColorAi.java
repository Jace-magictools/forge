package forge.ai;

import forge.card.MagicColor;
import forge.card.mana.ManaCostShard;
import forge.game.card.Card;
import forge.game.card.CardLists;
import forge.game.card.CardPredicates;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import java.util.List;

/**
 * magic-tools: rule 36, a basic land search (fetchland, Fertile Footsteps, Bountiful Landscape) takes the color
 * the hand asks for. Stock ChangeZoneAi.basicManaFixing took the basic type least present on the battlefield and
 * in hand, ties broken in WUBRG order: Genesis (27/10, Queza 6-4) with Island, Island and a hand of Lotus Cobra,
 * Dryad, Quandrix Cultivator, Savage Knuckleblade cracked Evolving Wilds for a Mountain, first spell on turn 11.
 * Rule 36b (tester 27/10, "without other info, an Island first, for the Ultimatum that asks for three blue"): when
 * the hand asks for none of the colors, the deck's most demanding spell decides, the color whose heaviest single
 * requirement (most pips of it in one spell of the library) is furthest from the sources, ties to the heavier one.
 * Rule 36c (tester 27/10): that spell comes first, except for a hand card cast before it that still lacks a color.
 */
public final class FetchColorAi {
    private FetchColorAi() { }

    /**
     * The basic type (among {@code basics}) to fetch (rule 36c, tester 27/10: "go for a blue, unless he has a green
     * card in hand that comes out before the ultimatum; the green cards bring colored mana, Cobra, Cultivator"):
     * first the color a nonland card in hand still lacks, if that card is cheaper than the deck's most demanding
     * spell (cheapest such card first); then the color that spell lacks most (rule 36b, library and hand); then the
     * color the hand asks for most per source (rule 36); null when none asks for any.
     */
    public static String neededBasicType(final Player ai, final List<String> basics) {
        final int[] supply = new int[MagicColor.WUBRG.length];
        for (final Card src : ai.getCardsIn(ZoneType.Battlefield)) {
            addSupply(supply, src);
        }
        final List<Card> hand = CardLists.filter(ai.getCardsIn(ZoneType.Hand), CardPredicates.NON_LANDS);
        for (final Card land : CardLists.filter(ai.getCardsIn(ZoneType.Hand), CardPredicates.LANDS)) {
            addSupply(supply, land);
        }

        // rule 36b: the most demanding spell, per color the most pips in one nonland card of the library or hand
        String plan = null;
        int planGap = 0;
        int planHeavy = 0;
        int planCmc = Integer.MAX_VALUE;
        final List<Card> deck = CardLists.filter(ai.getCardsIn(ZoneType.Library), CardPredicates.NON_LANDS);
        deck.addAll(hand);
        for (final String b : basics) {
            final int i = MagicColor.Constant.BASIC_LANDS.indexOf(b);
            if (i < 0) {
                continue;
            }
            for (final Card c : deck) {
                final int heavy = pips(c)[i];
                final int gap = heavy - supply[i];
                if (gap > 0 && (gap > planGap || (gap == planGap && heavy > planHeavy)
                        || (gap == planGap && heavy == planHeavy && c.getCMC() < planCmc))) {
                    plan = b;
                    planGap = gap;
                    planHeavy = heavy;
                    planCmc = c.getCMC();
                }
            }
        }

        // rule 36c: a hand card that comes out before that spell and still lacks a color
        String early = null;
        int earlyCmc = Integer.MAX_VALUE;
        int earlyGap = 0;
        for (final Card c : hand) {
            if (c.getCMC() >= planCmc) {
                continue;
            }
            final int[] p = pips(c);
            for (final String b : basics) {
                final int i = MagicColor.Constant.BASIC_LANDS.indexOf(b);
                if (i < 0) {
                    continue;
                }
                final int gap = p[i] - supply[i];
                if (gap > 0 && (c.getCMC() < earlyCmc || (c.getCMC() == earlyCmc && gap > earlyGap))) {
                    early = b;
                    earlyCmc = c.getCMC();
                    earlyGap = gap;
                }
            }
        }
        if (early != null) {
            return early;
        }
        if (plan != null) {
            return plan;
        }

        // rule 36: the color the hand asks for most per source
        final int[] demand = new int[MagicColor.WUBRG.length];
        for (final Card c : hand) {
            final int[] p = pips(c);
            for (int i = 0; i < p.length; i++) {
                demand[i] += p[i];
            }
        }
        String best = null;
        double bestScore = 0;
        for (final String b : basics) {
            final int i = MagicColor.Constant.BASIC_LANDS.indexOf(b);
            if (i < 0 || demand[i] == 0) {
                continue;
            }
            final double score = (double) demand[i] / (supply[i] + 1);
            if (score > bestScore) {
                best = b;
                bestScore = score;
            }
        }
        return best;
    }

    private static int[] pips(final Card c) {
        final int[] pips = new int[MagicColor.WUBRG.length];
        for (final ManaCostShard shard : c.getManaCost()) {
            final byte need = shard.getColorMask();
            for (int i = 0; i < MagicColor.WUBRG.length; i++) {
                if ((need & MagicColor.WUBRG[i]) != 0) {
                    pips[i]++;
                }
            }
        }
        return pips;
    }

    private static void addSupply(final int[] supply, final Card src) {
        byte mask = 0;
        for (final String color : src.getProducibleColors()) {
            mask |= MagicColor.fromName(color);
        }
        for (int i = 0; i < MagicColor.WUBRG.length; i++) {
            if ((mask & MagicColor.WUBRG[i]) != 0) {
                supply[i]++;
            }
        }
    }
}
