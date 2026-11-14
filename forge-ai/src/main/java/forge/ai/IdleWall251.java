package forge.ai;

import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardLists;
import forge.game.keyword.Keyword;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 251 (a tester report 08/11, D143 point 6, a test game turn 2): with no creature on
 * the other side, Wall of Omens was cast before Acquisitions Expert, which takes a card from a full hand only while the
 * hand is full. A defender's blocks are no use while the opponents have no creature, and its arrival keeps its value
 * later: when another creature (not a defender) of the hand competes for the same mana this turn, the defender waits.
 * Defenders with an activated ability (Wall of Roots, Overgrown Battlement) are not concerned.
 */
public final class IdleWall251 {
    private IdleWall251() { }

    static boolean idleWall(final Card c) {
        return c != null && c.isCreature() && c.hasKeyword(Keyword.DEFENDER) && c.getManaAbilities().isEmpty()
                && c.getSpellAbilities().stream().noneMatch(s -> s.isActivatedAbility() && !s.isSpell());
    }

    private static boolean noOpposingCreature(final Player ai) {
        for (final Player opp : ai.getOpponents()) {
            if (!opp.getCreaturesInPlay().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static int cost(final SpellAbility sa) {
        return sa.getPayCosts() != null && sa.getPayCosts().getTotalMana() != null
                ? sa.getPayCosts().getTotalMana().getCMC() : sa.getHostCard().getCMC();
    }

    /** rule 251: the creature of the hand that should go before the defender {@code sa}, or null */
    public static Card wallKept(final Player ai, final SpellAbility sa) {
        if (sa == null || !sa.isSpell() || !idleWall(sa.getHostCard())
                || !ai.getGame().getPhaseHandler().isPlayerTurn(ai) || !noOpposingCreature(ai)) {
            return null;
        }
        final CardCollection others = CardLists.filter(ai.getCardsIn(ZoneType.Hand),
                c -> c != sa.getHostCard() && c.isCreature() && !c.hasKeyword(Keyword.DEFENDER));
        if (others.isEmpty()) {
            return null;
        }
        final int available = ComputerUtilMana.getAvailableManaEstimate(ai, true);
        final int spent = cost(sa);
        for (final Card c : others) {
            for (final SpellAbility s : c.getSpells()) {
                s.setActivatingPlayer(ai);
                if (!s.canPlay() || !ComputerUtilMana.canPayManaCost(s, ai, 0, false)) {
                    continue;
                }
                if (available - spent < cost(s)) {
                    return c;
                }
            }
        }
        return null;
    }
}
