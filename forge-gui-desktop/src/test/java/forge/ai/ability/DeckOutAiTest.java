package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

public class DeckOutAiTest extends AITest {

    private Game setUp(int library) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard("Beast Whisperer", ai);
        addCard("Forest", ai);
        addCard("Forest", ai);
        addCardToZone("Grizzly Bears", ai, ZoneType.Hand);
        fillLibrary(ai, library);
        fillLibrary(opponent, 30);
        moveToMain2(game, ai);
        return game;
    }

    // Beast Whisperer makes it draw on every creature spell: with one card left, casting the Bears empties the library
    @Test
    public void testNoCreatureWhenWhispererEmptiesLibrary() {
        Game game = setUp(1);
        Player ai = game.getPlayers().get(1);
        gameLoopUntilNextPhase(game);
        assertEquals(ai.getCreaturesInPlay().size(), 1, "Grizzly Bears cast with one card left");
        assertEquals(ai.getCardsIn(ZoneType.Library).size(), 1);
    }

    @Test
    public void testCreatureCastWhenLibraryIsFine() {
        Game game = setUp(10);
        Player ai = game.getPlayers().get(1);
        gameLoopUntilNextPhase(game);
        assertEquals(ai.getCreaturesInPlay().size(), 2);
        assertEquals(ai.getCardsIn(ZoneType.Library).size(), 9);
    }

    private Game surge(int library) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        for (int i = 0; i < 10; i++) {
            addCard("Forest", ai);
        }
        addCardToZone("Primal Surge", ai, ZoneType.Hand);
        fillLibrary(ai, library);
        fillLibrary(opponent, 30);
        moveToMain2(game, ai);
        return game;
    }

    // règle 17 : Primal Surge on a library of permanents only (Wurm) put the whole library onto the battlefield,
    // the AI lost on its next draw; it now stops with 3 cards left
    @Test
    public void testPrimalSurgeKeepsLibrary() {
        Game game = surge(8);
        Player ai = game.getPlayers().get(1);
        gameLoopUntilNextPhase(game);
        assertEquals(ai.getCardsIn(ZoneType.Graveyard).size(), 1, "Primal Surge not cast");
        assertEquals(ai.getCardsIn(ZoneType.Library).size(), 3, "library emptied by Primal Surge");
        assertEquals(ai.getCreaturesInPlay().size(), 4);
    }

    @Test
    public void testPrimalSurgeGoesOnWithBigLibrary() {
        Game game = surge(20);
        Player ai = game.getPlayers().get(1);
        gameLoopUntilNextPhase(game);
        assertEquals(ai.getCardsIn(ZoneType.Library).size(), 3);
        assertEquals(ai.getCreaturesInPlay().size(), 16);
    }

    // règle 17 suite : with The Great Henge, each creature put by Primal Surge draws a card once the repeat is over;
    // Wurm stopped at 3 cards, then the Henge draws emptied the library and it lost (6 Wurm games out of 52, 25/10)
    @Test
    public void testPrimalSurgeCountsHengeDraws() {
        Game game = surge(12);
        Player ai = game.getPlayers().get(1);
        addCard("The Great Henge", ai).setTapped(true); // no mana left to cast the drawn bears
        gameLoopUntilNextPhase(game);
        assertEquals(ai.getCardsIn(ZoneType.Graveyard).size(), 1, "Primal Surge not cast");
        assertEquals(ai.getCardsIn(ZoneType.Library).size(), 3, "Henge draws not counted");
        assertEquals(ai.getCreaturesInPlay().size(), 4);
    }
}
