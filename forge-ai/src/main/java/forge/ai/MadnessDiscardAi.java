package forge.ai;

import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.cost.Cost;
import forge.game.keyword.KeywordInterface;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 113 (a tester report 30/10 18:16, Schizoph - Profanation, turn 5). Paying Rakdos Headliner's echo
 * (discard a card), the AI discarded a Mountain with two lands in play instead of Stromkirk Occultist, whose madness
 * cost {1}{R} it could pay right away.
 * General rule: when the AI discards cards of its own hand by its own choice, a permanent card with madness whose
 * madness cost it can pay now is discarded first (it gets cast for that cost instead of being lost). Skipped when the
 * ability that asks for the discard has a mana cost of its own (the mana may be spent by that cost).
 */
public final class MadnessDiscardAi {
    private MadnessDiscardAi() { }

    public static Card choose(final Player ai, final CardCollection validCards, final SpellAbility sa) {
        if (sa != null) {
            final SpellAbility root = sa.getRootAbility();
            if (root.getPayCosts() != null && root.getPayCosts().hasManaCost() && !root.isTrigger()
                    && root.getHostCard() != null && !root.getHostCard().isInZone(ZoneType.Stack)) {
                return null;
            }
        }
        Card best = null;
        for (final Card c : validCards) {
            if (!c.isPermanent() || c.isLand() || !c.isInZone(ZoneType.Hand) || !ai.equals(c.getOwner())) {
                continue;
            }
            final String cost = madnessCost(c);
            if (cost == null) {
                continue;
            }
            final SpellAbility spell = c.getFirstSpellAbility();
            if (spell == null || !spell.isSpell()) {
                continue;
            }
            spell.setActivatingPlayer(ai);
            final Cost abCost = "ManaCost".equals(cost) ? new Cost(c.getManaCost(), false) : new Cost(cost, false);
            final SpellAbility madness = spell.copyWithManaCostReplaced(ai, abCost);
            if (!ComputerUtilCost.canPayCost(madness, ai, true)) {
                continue;
            }
            if (best == null || c.getCMC() > best.getCMC()) {
                best = c;
            }
        }
        return best;
    }

    static String madnessCost(final Card c) {
        for (final KeywordInterface k : c.getKeywords()) {
            final String s = k.getOriginal();
            if (s.startsWith("Madness:")) {
                return s.split(":")[1];
            }
        }
        return null;
    }
}
