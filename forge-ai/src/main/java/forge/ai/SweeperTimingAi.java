package forge.ai;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.CardCollectionView;
import forge.game.combat.CombatUtil;
import forge.game.phase.PhaseHandler;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;

/**
 * magic-tools: rule 115 (a tester report 30/10 18:20, Schizoph - Profanation, turn 8). The AI cast Drown in Sorrow
 * (all creatures -2/-2) in its precombat main phase: its Akroma, Angel of Wrath attacked as a 4/4 instead of a 6/6,
 * while none of the creatures the spell killed could block a flyer anyway.
 * General rule: a "-X/-X to all" spell that shrinks one of our own possible attackers (it survives the spell) waits
 * for the postcombat main phase when none of the opposing creatures it would kill could block that attacker. The
 * sweeper kills the same creatures after combat, and our attackers hit with their full power.
 */
public final class SweeperTimingAi {
    private SweeperTimingAi() { }

    /**
     * @param ownAffected our creatures the spell affects (before keeping only the ones it kills)
     * @param ownKilled our creatures it kills
     * @param oppKilled opposing creatures it kills
     * @param power the power change (negative)
     * @return true = wait for main 2
     */
    public static boolean waitForMain2(final Player ai, final SpellAbility sa, final CardCollectionView ownAffected,
            final CardCollectionView ownKilled, final CardCollectionView oppKilled, final int power) {
        if (power >= 0 || ai == null || sa == null) {
            return false;
        }
        final Game game = ai.getGame();
        final PhaseHandler ph = game.getPhaseHandler();
        if (!ph.isPlayerTurn(ai) || !ph.getPhase().isBefore(PhaseType.COMBAT_DECLARE_ATTACKERS)
                || ph.getPhase().isBefore(PhaseType.MAIN1)) {
            return false;
        }
        if (!game.getStack().isEmpty()) {
            return false;
        }
        for (final Card att : ownAffected) {
            if (ownKilled.contains(att) || att.getNetPower() <= 0 || !CombatUtil.canAttack(att)) {
                continue;
            }
            boolean someDefender = false;
            for (final Player opp : ai.getOpponents()) {
                if (CombatUtil.canAttack(att, opp)) {
                    someDefender = true;
                    break;
                }
            }
            if (!someDefender) {
                continue;
            }
            boolean blockableByVictim = false;
            for (final Card victim : oppKilled) {
                if (CombatUtil.canBlock(att, victim)) {
                    blockableByVictim = true;
                    break;
                }
            }
            if (!blockableByVictim) {
                return true;
            }
        }
        return false;
    }
}
