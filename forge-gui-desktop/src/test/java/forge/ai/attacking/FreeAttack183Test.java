package forge.ai.attacking;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;

import static org.testng.Assert.assertTrue;

// magic-tools rule 183 (D105): a creature that no block can kill attacks, even if it cannot kill the blocker
// (Acoucheur d'Uruk 3/2 against Sam Gamegie, protection from black). Here Grizzly Bears against a Wall of Wood 0/3.
// Control: a Hill Giant across the table can kill the Bears, they stay home.
public class FreeAttack183Test extends AITest {

    private Player[] game(String oppCreature) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard(oppCreature, opponent).setSickness(false);
        addCard("Grizzly Bears", ai).setSickness(false);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        ai.addLandPlayedThisTurn();
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.MAIN2);
        return new Player[] {ai, opponent};
    }

    @Test(timeOut = 120000)
    public void testAttacksIntoHarmlessWall() {
        Player[] p = game("Wall of Wood");
        assertTrue(p[0].getCreaturesInPlay().anyMatch(c -> c.isTapped()) || p[1].getLife() < 20,
                "Bears attacked, opp life " + p[1].getLife());
    }

    @Test(timeOut = 120000)
    public void testStaysHomeAgainstKiller() {
        Player[] p = game("Hill Giant");
        assertTrue(p[1].getLife() == 20 && p[0].getCreaturesInPlay().size() == 1, "Bears stayed home, opp life " + p[1].getLife());
    }
}
