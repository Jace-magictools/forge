package forge.ai.attacking;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

// magic-tools rule 257 (D147 point 3, tester 08/11, a test game decisions 46-47): at 2 life facing
// flyers none of our ground creatures can block, the AI stayed home and cast Winota in main 2. Doomed on defence, it
// attacks, and Winota (attack trigger on non-Humans) comes down in main 1 first. Control: at 20 life it stays home.
public class DoomedAttack257Test extends AITest {

    private Player[] game(int life) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        opponent.setLife(30, null);
        ai.setLife(life, null);
        addCard("Serra Angel", opponent).setSickness(false);
        addCard("Serra Angel", opponent).setSickness(false);
        addCard("Wall of Stone", opponent).setSickness(false);
        addCards("Mountain", 2, ai);
        addCards("Plains", 2, ai);
        addCard("Grizzly Bears", ai).setSickness(false);
        addCard("Grizzly Bears", ai).setSickness(false);
        addCardToZone("Winota, Joiner of Forces", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        ai.addLandPlayedThisTurn();
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        assertEquals(forge.ai.DoomedAttack257.doomed(ai), life <= 8, "doomed prediction");
        playUntilPhase(game, PhaseType.COMBAT_DECLARE_BLOCKERS);
        return new Player[] {ai, opponent};
    }

    @Test(timeOut = 120000)
    public void testDoomedAttacksWithWinotaFirst() {
        Player[] p = game(2);
        Game game = p[0].getGame();
        assertTrue(game.getCombat() != null && !game.getCombat().getAttackers().isEmpty(), "attacked");
        assertEquals(countCardsWithName(game, "Winota, Joiner of Forces", ZoneType.Battlefield), 1, "Winota in main 1");
    }

    @Test(timeOut = 120000)
    public void testSafeStaysHome() {
        Player[] p = game(20);
        Game game = p[0].getGame();
        assertTrue(game.getCombat() == null || game.getCombat().getAttackers().isEmpty(), "stayed home");
    }
}
