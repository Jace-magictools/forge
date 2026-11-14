package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;

import static org.testng.Assert.assertTrue;

// magic-tools rule 143: Vizier of Remedies (replacement "N -1/-1 counters minus one") works like Melira for the persist
// loop: Murderous Redcap + Cartel Aristocrat (free sacrifice outlet) = endless damage. Puzzle MT13.
public class VizierLoop143Test extends AITest {

    private Player loopGame(boolean vizier) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        if (vizier) {
            addCard("Vizier of Remedies", ai);
        }
        addCard("Murderous Redcap", ai);
        addCard("Cartel Aristocrat", ai);
        for (int i = 0; i < 4; i++) {
            addCard("Wall of Stone", opponent);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilNextTurn(game);
        return opponent;
    }

    @Test(timeOut = 120000)
    public void testVizierRedcapAristocratLoops() {
        Player opponent = loopGame(true);
        assertTrue(opponent.getLife() <= 0, "life " + opponent.getLife());
    }

    // without Vizier the Redcap comes back once with a counter: no endless loop
    @Test(timeOut = 120000)
    public void testWithoutVizierNoLoop() {
        Player opponent = loopGame(false);
        assertTrue(opponent.getLife() >= 16, "life " + opponent.getLife());
    }
}
