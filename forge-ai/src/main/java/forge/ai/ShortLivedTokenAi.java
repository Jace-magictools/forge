package forge.ai;

import forge.game.combat.Combat;
import forge.game.phase.PhaseHandler;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;

/**
 * magic-tools: rule 24, short-lived tokens on the opponent's turn. Tokens that leave at the next end step or
 * at our next upkeep (AtEOT: Force of Rage, Ball Lightning style spells) never attack if made on the
 * opponent's turn: haste does not count there, they can only block. Forge let haste through and Precipitron
 * pitched Force of Rage (two cards) in the opponent's upkeep on turn 2, with nothing to block.
 * On the opponent's turn they are made only once attackers are declared; on our turn nothing changes.
 * Tokens that stay until our own end step (AtEOT YourSacrifice / YourExile) are left alone: made at the
 * opponent's end step they attack on our turn.
 */
public final class ShortLivedTokenAi {
    private ShortLivedTokenAi() { }

    public static boolean leavesBeforeOurCombat(final SpellAbility sa) {
        if (!sa.hasParam("AtEOT")) {
            return false;
        }
        final String when = sa.getParam("AtEOT");
        return !"YourSacrifice".equals(when) && !"YourExile".equals(when);
    }

    /** True when making these tokens now, on the opponent's turn, gives nothing: no attacker to block. */
    public static boolean wastedNow(final Player ai, final SpellAbility sa, final PhaseHandler ph) {
        if (ph.isPlayerTurn(ai) || !leavesBeforeOurCombat(sa)) {
            return false;
        }
        if (ph.is(PhaseType.COMBAT_DECLARE_ATTACKERS)) {
            final Combat combat = ai.getGame().getCombat();
            if (combat != null && !combat.getAttackersOf(ai).isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
