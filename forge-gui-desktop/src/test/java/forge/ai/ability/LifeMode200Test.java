package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 200, a tester report 02/11 (a test game): Charming Prince at 20 life scries instead of gaining 3.
public class LifeMode200Test extends AITest {

    private Game play(int life) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        ai.setLife(life, null);
        addCard("Plains", ai);
        addCard("Plains", ai);
        addCardToZone("Charming Prince", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, opponent);
        game.getAction().checkStateEffects(true);
        playUntilNextTurn(game);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        return game;
    }

    /** The modes are shuffled, the gain came out one time in six or so: a few games to see it */
    @Test
    public void testNoGainAtTwentyLife() {
        for (int i = 0; i < 12; i++) {
            Game game = play(20);
            Player ai = game.getPlayers().get(1);
            assertEquals(countCardsWithName(game, "Charming Prince", ZoneType.Battlefield), 1, gameStateToString(game));
            assertEquals(ai.getLife(), 20, gameStateToString(game));
        }
    }

    /** At low life the choice stays Forge's: the gain does come out */
    @Test
    public void testGainStaysPossibleAtLowLife() {
        int gained = 0;
        for (int i = 0; i < 12; i++) {
            Game game = play(5);
            if (game.getPlayers().get(1).getLife() > 5) {
                gained++;
            }
        }
        org.testng.Assert.assertTrue(gained > 0);
    }
}
