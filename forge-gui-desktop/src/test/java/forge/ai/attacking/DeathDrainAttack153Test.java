package forge.ai.attacking;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;

import static org.testng.Assert.assertTrue;

// magic-tools rule 153: the opponent is at 1 life and Blood Artist drains 1 on every death. Grizzly Bears into
// Serra Angels: blocked, a Bear dies and drains; unblocked, it deals 2. Any attack wins (Plateau 02/11, MT32).
public class DeathDrainAttack153Test extends AITest {

    private Player[] game(boolean artist) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        opponent.setLife(1, null);
        addCard("Serra Angel", opponent);
        addCard("Serra Angel", opponent);
        addCard("Vizzerdrix", opponent);
        if (artist) {
            addCard("Blood Artist", ai).setSickness(false);
        }
        addCard("Grizzly Bears", ai).setSickness(false);
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
    public void testAttacksWhenOneDeathIsLethal() {
        Player[] p = game(true);
        assertTrue(p[1].getLife() <= 0 || p[1].hasLost(), "opponent dead, life " + p[1].getLife());
    }

    // without Blood Artist, Bears into Serra Angels is a bad attack: the AI stays home
    @Test(timeOut = 120000)
    public void testWithoutDrainNoSuicide() {
        Player[] p = game(false);
        assertTrue(p[1].getLife() == 1, "opponent at 1, life " + p[1].getLife());
    }
}
