package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

public class UntapAllEtbAiTest extends AITest {

    // Deepway Navigator: "untap each other Merfolk you control" does nothing without tapped Merfolk,
    // that is no reason to keep a 2/2 for 2 in hand (BadEtbEffects)
    @Test
    public void testCastWithNothingToUntap() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);

        addCard("Plains", ai);
        addCard("Island", ai);
        addCardToZone("Deepway Navigator", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 30);

        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        gameLoopUntilNextPhase(game);

        assertEquals(ai.getCreaturesInPlay().size(), 1, "Deepway Navigator kept in hand");
    }
}
