package forge.ai.attacking;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.ai.DeckPlanAi;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

// magic-tools rule 282 (Route$ du plan, it. 24 du 10/11): Forge has a "non aggro deck" attack mode (keep a blocker
// when the counterattack costs more life than the attack deals) but never turns it on. A deck whose plan route is
// combo or reach turns it on. Wind Drake (2 in the air) against a 4/4 on the ground at 8 life: the reach deck keeps it
// home as a chump blocker; the same deck without a plan swings.
public class NonAggroRoute282Test extends AITest {

    private Game game(String route) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        if (route != null) {
            ai.getRegisteredPlayer().getDeck().setAiHints("Route$" + route);
        }
        ai.setLife(8, null);
        opponent.setLife(20, null);
        addCard("Wind Drake", ai).setSickness(false);
        addCard("Island", ai);
        addCard("Craw Wurm", opponent).setSickness(false);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        ai.addLandPlayedThisTurn();
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        assertEquals(DeckPlanAi.nonAggroRoute(ai), "reach".equals(route) || "combo".equals(route), "route read");
        playUntilPhase(game, PhaseType.COMBAT_DECLARE_BLOCKERS);
        return game;
    }

    private static boolean attacked(Game game) {
        return game.getCombat() != null && !game.getCombat().getAttackers().isEmpty();
    }

    @Test(timeOut = 120000)
    public void testReachKeepsBlocker() {
        assertTrue(!attacked(game("reach")), "reach deck stays home");
    }

    @Test(timeOut = 120000)
    public void testNoPlanAttacks() {
        assertTrue(attacked(game(null)), "no plan: attacks");
    }

    @Test(timeOut = 120000)
    public void testPressureAttacks() {
        assertTrue(attacked(game("pressure")), "pressure: attacks");
    }
}
