package forge.ai.attacking;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;

import static org.testng.Assert.assertTrue;

// magic-tools rule 154: a decayed Zombie token (Jadar) with Blood Artist out. Into two Serra Angels the token dies if
// blocked, but its death drains 1, unblocked it deals 2: the attack costs nothing (Plateau 02/11, MT33).
public class FreeDrainAttacker154Test extends AITest {

    private Player[] game(boolean artist) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        opponent.setLife(10, null);
        addCard("Serra Angel", opponent);
        addCard("Serra Angel", opponent);
        if (artist) {
            addCard("Blood Artist", ai).setSickness(false);
        }
        addToken("b_2_2_zombie_decayed", ai).setSickness(false);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        ai.addLandPlayedThisTurn();
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.MAIN2);
        return new Player[] {ai, opponent};
    }

    @Test(timeOut = 120000)
    public void testTokenAttacksWhenItsDeathDrains() {
        Player[] p = game(true);
        assertTrue(p[1].getLife() < 10, "opponent lost life, life " + p[1].getLife());
    }

    // without Blood Artist the token dies for nothing: it stays home
    @Test(timeOut = 120000)
    public void testWithoutDrainStaysHome() {
        Player[] p = game(false);
        assertTrue(p[1].getLife() == 10, "opponent at 10, life " + p[1].getLife());
    }
}
