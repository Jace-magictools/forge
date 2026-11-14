package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

public class LootingAiTest extends AITest {

    // Faithless Looting is no longer removed from AI decks: the AI loots with cards in hand
    @Test
    public void testAiCastsFaithlessLooting() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);

        addCard("Mountain", ai);
        addCardToZone("Faithless Looting", ai, ZoneType.Hand);
        addCardToZone("Craw Wurm", ai, ZoneType.Hand);
        addCardToZone("Craw Wurm", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 30);

        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        gameLoopUntilNextPhase(game);

        assertEquals(countCardsWithName(game, "Faithless Looting", ZoneType.Graveyard), 1);
    }

    // With an empty hand, looting only throws away the two cards drawn: the AI keeps it
    @Test
    public void testNoLootingWithEmptyHand() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);

        addCard("Mountain", ai);
        addCardToZone("Faithless Looting", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 30);

        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        gameLoopUntilNextPhase(game);

        assertEquals(countCardsWithName(game, "Faithless Looting", ZoneType.Hand), 1);
    }
}
