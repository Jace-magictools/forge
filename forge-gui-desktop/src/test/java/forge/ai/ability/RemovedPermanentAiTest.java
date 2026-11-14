package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertTrue;

/** Rule 46: a permanent marked AI:RemoveDeck:All is still cast from hand (the deck chose it), its abilities stay out of reach. */
public class RemovedPermanentAiTest extends AITest {

    private Game main2(String card, String land, int lands) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        for (int i = 0; i < lands; i++) {
            addCard(land, ai);
        }
        addCardToZone(card, ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        moveToMain2(game, ai);
        return game;
    }

    private void assertCast(String card, String land, int lands) {
        Game game = main2(card, land, lands);
        Player ai = game.getPlayers().get(1);
        gameLoopUntilNextPhase(game);
        assertTrue(ai.getCardsIn(ZoneType.Battlefield).anyMatch(c -> c.getName().equals(card)), card + " kept in hand");
    }

    @Test
    public void testSylvanLibraryCast() {
        assertCast("Sylvan Library", "Forest", 2);
    }

    @Test
    public void testTradingPostCast() {
        assertCast("Trading Post", "Plains", 4);
    }

    @Test
    public void testAdantoVanguardCast() {
        assertCast("Adanto Vanguard", "Plains", 2);
    }

    // the ability of a cast Trading Post stays out of the AI's choices
    @Test
    public void testTradingPostAbilityUnused() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard("Trading Post", ai);
        addCard("Plains", ai);
        addCard("Plains", ai);
        addCardToZone("Plains", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        moveToMain2(game, ai);
        gameLoopUntilNextPhase(game);
        assertTrue(ai.getCardsIn(ZoneType.Battlefield).anyMatch(c -> c.getName().equals("Trading Post") && !c.isTapped()), "Trading Post activated");
    }
}
