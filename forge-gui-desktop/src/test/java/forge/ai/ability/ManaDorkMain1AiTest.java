package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

public class ManaDorkMain1AiTest extends AITest {

    // Fyndhorn Elves would attack an empty board: cast Elvish Harbinger (2G) in main 1 with its mana
    @Test
    public void testCastBeforeManaElfAttacks() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);

        addCards("Forest", 2, ai);
        addCard("Fyndhorn Elves", ai).setSickness(false);
        addCardToZone("Elvish Harbinger", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 30);

        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        gameLoopUntilNextPhase(game);

        assertEquals(countCardsWithName(game, "Elvish Harbinger", ZoneType.Battlefield), 1);
    }

    // no mana creature: stock behaviour, the creature waits for main 2
    @Test
    public void testNoManaCreatureWaitsForMain2() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);

        addCards("Forest", 3, ai);
        addCard("Grizzly Bears", ai).setSickness(false);
        addCardToZone("Elvish Harbinger", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 30);

        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        gameLoopUntilNextPhase(game);

        assertEquals(countCardsWithName(game, "Elvish Harbinger", ZoneType.Battlefield), 0);
    }
}
