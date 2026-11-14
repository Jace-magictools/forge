package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

public class DigDeckOutAiTest extends AITest {

    private Game setUp(int library) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard("Forest", ai);
        addCard("Forest", ai);
        addCard("Forest", ai);
        addCardToZone("Lead the Stampede", ai, ZoneType.Hand);
        fillLibrary(ai, library);
        fillLibrary(opponent, 30);
        moveToMain2(game, ai);
        return game;
    }

    // Lead the Stampede takes every creature of the top five: with three creatures left the library would be empty
    @Test
    public void testNoStampedeWhenItEmptiesLibrary() {
        Game game = setUp(3);
        Player ai = game.getPlayers().get(1);
        gameLoopUntilNextPhase(game);
        assertEquals(ai.getCardsIn(ZoneType.Library).size(), 3, "Lead the Stampede cast with three cards left");
        assertEquals(ai.getCardsIn(ZoneType.Hand).size(), 1);
    }

    @Test
    public void testStampedeCastWhenLibraryIsFine() {
        Game game = setUp(10);
        Player ai = game.getPlayers().get(1);
        gameLoopUntilNextPhase(game);
        assertEquals(ai.getCardsIn(ZoneType.Library).size(), 5);
    }
}
