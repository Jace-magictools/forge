package forge.ai;

import java.util.List;

import forge.game.Game;
import forge.game.GameObject;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardLists;
import forge.game.combat.Combat;
import forge.game.phase.PhaseHandler;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;

/**
 * magic-tools: grenouille (tester) bounces its own creatures to replay their ETB (Pond Prophet, Dreamdew
 * Entrancer, Nadu…). Mistbreath Elder and Run Away Together were marked AI:RemoveDeck:All.
 * - Mistbreath Elder (AILogic$ MistbreathElder): the upkeep bounce takes the cheapest nontoken creature
 *   with an ETB, never a token unless nothing else.
 * - Run Away Together (AILogic$ RunAwayTogether): saves our creature from the top of the stack or a lost
 *   combat, or at the opponent's end step replays one of our ETB creatures, with the best opposing
 *   creature (a token first: it is gone for good).
 */
public final class FrogBounceAi {
    private FrogBounceAi() { }

    public static boolean isElder(final SpellAbility sa) {
        return "MistbreathElder".equals(sa.getParam("AILogic"));
    }

    public static boolean isRunAwayTogether(final SpellAbility sa) {
        return "RunAwayTogether".equals(sa.getParam("AILogic"));
    }

    private static Card cheapestEtb(final CardCollection list) {
        Card best = null;
        for (final Card c : list) {
            if (c.isToken() || !c.hasETBTrigger(false)) {
                continue;
            }
            if (best == null || c.getCMC() < best.getCMC()
                    || (c.getCMC() == best.getCMC() && ComputerUtilCard.evaluateCreature(c) > ComputerUtilCard.evaluateCreature(best))) {
                best = c;
            }
        }
        return best;
    }

    /** Mistbreath Elder's upkeep trigger: which of our creatures goes back to hand. */
    public static Card chooseElderBounce(final Player ai, final CardCollection fetchList) {
        final CardCollection own = CardLists.filter(fetchList, c -> c.getOwner().equals(ai));
        final Card etb = cheapestEtb(own);
        if (etb != null) {
            return etb;
        }
        final CardCollection nontoken = CardLists.filter(own, c -> !c.isToken());
        if (!nontoken.isEmpty()) {
            return ComputerUtilCard.getWorstCreatureAI(nontoken);
        }
        return ComputerUtilCard.getWorstCreatureAI(fetchList);
    }

    private static boolean threatened(final Player ai, final Card c) {
        final Game game = ai.getGame();
        if (!game.getStack().isEmpty()) {
            final List<GameObject> objects = ComputerUtil.predictThreatenedObjects(ai, null, true);
            if (objects.contains(c)) {
                return true;
            }
        }
        final Combat combat = game.getCombat();
        return combat != null && game.getPhaseHandler().is(PhaseType.COMBAT_DECLARE_BLOCKERS)
                && (combat.isAttacking(c) || combat.isBlocking(c))
                && ComputerUtilCombat.combatantWouldBeDestroyed(ai, c, combat);
    }

    private static Card bestOpposing(final CardCollection opp) {
        final CardCollection tokens = CardLists.filter(opp, Card::isToken);
        if (!tokens.isEmpty()) {
            return ComputerUtilCard.getBestCreatureAI(tokens);
        }
        return ComputerUtilCard.getBestCreatureAI(opp);
    }

    /** Run Away Together: picks both targets and says whether to cast it now. */
    public static boolean chooseRunAwayTargets(final Player ai, final SpellAbility sa) {
        final Game game = ai.getGame();
        final CardCollection own = CardLists.filter(
                CardLists.getTargetableCards(ai.getCreaturesInPlay(), sa), c -> !c.isToken());
        final CardCollection opp = CardLists.getTargetableCards(
                CardLists.filter(game.getCardsIn(forge.game.zone.ZoneType.Battlefield),
                        c -> c.isCreature() && c.getController().isOpponentOf(ai)), sa);
        if (own.isEmpty() || opp.isEmpty()) {
            return false;
        }
        Card mine = null;
        final CardCollection saved = CardLists.filter(own, c -> threatened(ai, c));
        if (!saved.isEmpty()) {
            mine = ComputerUtilCard.getBestCreatureAI(saved);
        } else {
            final PhaseHandler ph = game.getPhaseHandler();
            if (game.getStack().isEmpty() && ph.is(PhaseType.END_OF_TURN) && !ph.isPlayerTurn(ai)) {
                mine = cheapestEtb(own);
            }
        }
        if (mine == null) {
            return false;
        }
        final Card theirs = bestOpposing(opp);
        sa.resetTargets();
        sa.getTargets().add(mine);
        sa.getTargets().add(theirs);
        return sa.isTargetNumberValid();
    }
}
