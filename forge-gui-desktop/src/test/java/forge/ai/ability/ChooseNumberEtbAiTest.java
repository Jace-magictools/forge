package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

public class ChooseNumberEtbAiTest extends AITest {

    // Haktos chooses 2, 3 or 4 at random as it enters: the choice no longer keeps the 6/1 in hand
    @Test
    public void testRandomNumberOnEntryIsCast() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);

        addCards("Mountain", 2, ai);
        addCards("Plains", 2, ai);
        addCardToZone("Haktos the Unscarred", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 30);

        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        gameLoopUntilNextPhase(game);
        assertEquals(countCardsWithName(game, "Haktos the Unscarred", ZoneType.Battlefield), 1);
    }
}
