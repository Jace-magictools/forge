package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertTrue;

// magic-tools rule 314 (D178, plateau 13/11, tester, a test game): the opponent is at 0 life or less
// and only Platinum Angel keeps them in the game. Removing the Angel wins: Stadium Headliner is sacrificed on it, a
// removal spell goes on it rather than on a bigger creature.
public class UnlockWin314Test extends AITest {

    private Game setup(int oppLife, String hand, String oppOther) {
        Game game = initAndCreateGame();
        Player opp = game.getPlayers().get(0);
        Player ai = game.getPlayers().get(1);
        addCard("Platinum Angel", opp);
        if (oppOther != null) {
            addCard(oppOther, opp);
        }
        opp.setLife(oppLife, null);
        for (int i = 0; i < 3; i++) {
            addCard("Mountain", ai);
            addCard("Swamp", ai);
        }
        if (hand == null) {
            addCard("Stadium Headliner", ai);
            for (int i = 0; i < 4; i++) {
                addCard("Grizzly Bears", ai);
            }
        } else {
            addCardToZone(hand, ai, ZoneType.Hand);
        }
        for (Player p : game.getPlayers()) {
            fillLibrary(p, 20);
        }
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        return game;
    }

    @Test(timeOut = 120000)
    public void testHeadlinerKillsTheAngel() {
        Game game = setup(-5, null, null);
        Player opp = game.getPlayers().get(0);
        playUntilPhase(game, PhaseType.CLEANUP);
        assertTrue(opp.hasLost(), "Stadium Headliner kept, the opponent did not lose");
    }

    @Test(timeOut = 120000)
    public void testRemovalOnTheAngelNotTheBiggest() {
        Game game = setup(0, "Murder", "Craw Wurm");
        Player opp = game.getPlayers().get(0);
        playUntilPhase(game, PhaseType.CLEANUP);
        assertTrue(opp.hasLost(), "Murder not on Platinum Angel, the opponent did not lose");
    }

    @Test(timeOut = 120000)
    public void testOwnPersecutorDestroyed() {
        Game game = initAndCreateGame();
        Player opp = game.getPlayers().get(0);
        Player ai = game.getPlayers().get(1);
        addCard("Abyssal Persecutor", ai);
        addCard("Craw Wurm", opp);
        opp.setLife(-2, null);
        for (int i = 0; i < 3; i++) {
            addCard("Swamp", ai);
        }
        addCardToZone("Murder", ai, ZoneType.Hand);
        for (Player p : game.getPlayers()) {
            fillLibrary(p, 20);
        }
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.CLEANUP);
        assertTrue(opp.hasLost(), "Murder not on our own Abyssal Persecutor, the opponent did not lose");
    }
}
