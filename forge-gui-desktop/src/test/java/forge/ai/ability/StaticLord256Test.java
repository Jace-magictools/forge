package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools rule 256 (D147 point 1, tester 08/11, a test game): Control Magic (AI) took Alpine
// Watchdog 3/3 (Pack Leader) instead of Blade Historian, whose static gives double strike to all attackers. A lord's
// static now counts for each creature it pumps. Control: a vanilla 2/3 for 4 in the Historian's seat, the Watchdog
// stays the target.
public class StaticLord256Test extends AITest {

    private Player stealer(String third) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        for (String n : new String[] {"Alpine Watchdog", "Pack Leader", "Igneous Cur", third}) {
            addCard(n, opponent).setSickness(false);
        }
        for (int i = 0; i < 4; i++) {
            addCard("Island", ai);
        }
        addCardToZone("Control Magic", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        ai.addLandPlayedThisTurn();
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        return ai;
    }

    private static String stolen(Player ai) {
        for (Card c : ai.getCreaturesInPlay()) {
            return c.getName();
        }
        return "none";
    }

    @Test(timeOut = 120000)
    public void testStealsTheHistorian() {
        assertEquals(stolen(stealer("Blade Historian")), "Blade Historian");
    }

    @Test(timeOut = 120000)
    public void testStealsTheWatchdogWithoutLord() {
        assertEquals(stolen(stealer("Wall of Omens")), "Alpine Watchdog");
    }
}
