package forge.ai;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.CardCopyService;
import forge.game.keyword.Keyword;
import forge.game.phase.PhaseHandler;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.trigger.Trigger;

/**
 * magic-tools, règle 195 : Reinforced Ronin was cast in front of bigger untapped blockers instead of a real creature
 * (tester 2/11 14:09): it could not attack and came back to hand. General rule: a haste creature that leaves at
 * the end step (returned to hand or sacrificed: Ball Lightning, Spark Elemental, Viashino Sandscout...) is only worth
 * its mana if it attacks this turn, so it is cast before combat and only if the attack logic would send it.
 */
public final class LeavesAtEndStepAi {
    private LeavesAtEndStepAi() { }

    public static boolean applies(final Card c) {
        if (!c.isCreature() || !c.hasKeyword(Keyword.HASTE)) {
            return false;
        }
        if (c.hasSVar("EndOfTurnLeavePlay")) {
            return true;
        }
        // read the script, not t.ensureAbility(): building the trigger's ability can change later AI choices
        for (final Trigger t : c.getTriggers()) {
            if (!"End of Turn".equals(t.getParam("Phase")) || t.hasParam("IsPresent") || t.hasParam("CheckSVar")
                    || t.hasParam("Condition")) {
                continue;
            }
            final String who = t.getParam("ValidPlayer");
            if (who != null && !"You".equals(who) && !"Player".equals(who)) {
                continue;
            }
            final String exec = t.getParam("Execute");
            if (exec != null && c.hasSVar(exec) && leavesPlay(c.getSVar(exec))) {
                return true;
            }
        }
        return false;
    }

    static boolean leavesPlay(final String sv) {
        if (sv.contains("Condition") || sv.contains("UnlessCost")) {
            return false;
        }
        final boolean self = !sv.contains("Defined$") || sv.contains("Defined$ Self");
        if (sv.contains("$ ChangeZone")) {
            return self && sv.contains("Origin$ Battlefield") && sv.contains("Destination$ Hand");
        }
        return sv.contains("$ Sacrifice") && (sv.contains("SacValid$ Self") || sv.contains("Defined$ Self"));
    }

    /** Cast it now? Only on the AI's turn before attackers, and only if it would attack. */
    public static boolean wantToCast(final Player ai, final Card card, final PhaseHandler ph) {
        final Game game = ai.getGame();
        if (!ph.isPlayerTurn(ai) || !ph.getPhase().isBefore(PhaseType.COMBAT_DECLARE_ATTACKERS)
                || game.getReplacementHandler().wouldPhaseBeSkipped(ai, PhaseType.COMBAT_BEGIN)) {
            return false;
        }
        final Card copy = CardCopyService.getLKICopy(card);
        copy.setSickness(false);
        return ComputerUtilCard.doesSpecifiedCreatureAttackAI(ai, copy);
    }
}
