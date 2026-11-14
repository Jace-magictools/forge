package forge.ai;

import forge.game.ability.AbilityUtils;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.phase.PhaseHandler;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 258 (D147 point 4, tester 08/11, a test game decision 10): turn 2, three Swamps and
 * a hand of seven, Sign in Blood was cast instead of Vampire Nighthawk, and a card was discarded at end of turn. A
 * draw spell whose cards would push the hand over its maximum size this turn waits when the mana it eats would leave
 * unpaid a spell of the hand at least as expensive that can be paid now: the cards drawn are partly lost, the
 * development is not. Cast after that spell if the mana remains, or on a later turn.
 */
public final class DrawFullHand258 {
    private DrawFullHand258() { }

    private static int cost(final SpellAbility sa) {
        return sa.getPayCosts() != null && sa.getPayCosts().getTotalMana() != null
                ? sa.getPayCosts().getTotalMana().getCMC() : sa.getHostCard().getCMC();
    }

    /** rule 258: the spell of the hand that the draw spell {@code sa} would leave unpaid this turn, or null */
    public static Card drawKept(final Player ai, final SpellAbility sa) {
        if (sa == null || !sa.isSpell() || sa.getApi() != ApiType.Draw) {
            return null;
        }
        final Card host = sa.getHostCard();
        final PhaseHandler ph = ai.getGame().getPhaseHandler();
        if (!ph.isPlayerTurn(ai) || !ph.getPhase().isMain()) {
            return null;
        }
        final int max = ai.getMaxHandSize();
        if (max < 0 || ai.isUnlimitedHandSize()) {
            return null;
        }
        final int drawn = sa.hasParam("NumCards") ? AbilityUtils.calculateAmount(host, sa.getParam("NumCards"), sa) : 1;
        final int inHand = ai.getCardsIn(ZoneType.Hand).size() - (host.isInZone(ZoneType.Hand) ? 1 : 0);
        if (inHand + drawn <= max) {
            return null;
        }
        // one mana per untapped source (see rule 253)
        final int available = ComputerUtilMana.getAvailableManaSources(ai, true).size() + ai.getManaPool().totalMana();
        final int draw = cost(sa);
        final int after = available - draw;
        for (final Card c : ai.getCardsIn(ZoneType.Hand)) {
            if (c == host || c.isLand() || c.isInstant() || c.hasKeyword(forge.game.keyword.Keyword.FLASH)) {
                continue;
            }
            for (final SpellAbility s : c.getSpells()) {
                s.setActivatingPlayer(ai);
                final int need = cost(s);
                if (need < draw || need <= after || need > available || s.getApi() == ApiType.Draw) {
                    continue;
                }
                if (s.canPlay() && ComputerUtilMana.canPayManaCost(s, ai, 0, false)) {
                    return c;
                }
            }
        }
        return null;
    }
}
