package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertTrue;

// magic-tools: rule 175, an empty Pump that carries a target on an opponent's permanent is cast
// for its sub-abilities (ketramos: Legions to Ashes held 10 turns, never cast)
public class OppCardCarrier175Test extends AITest {

    @Test
    public void testLegionsToAshesExilesThreat() {
        Game game = initAndCreateGame();
        Player opp = game.getPlayers().get(0);
        Player ai = game.getPlayers().get(1);
        addCard("Plains", ai);
        addCard("Plains", ai);
        addCard("Swamp", ai);
        addCardToZone("Legions to Ashes", ai, ZoneType.Hand);
        addCard("Serra Angel", opp);
        addCard("Mountain", opp);
        fillLibrary(opp, 20);
        fillLibrary(ai, 20);
        moveToMain2(game, ai);
        gameLoopUntilNextPhase(game);
        String zones = "opp bf=" + opp.getCardsIn(ZoneType.Battlefield) + " exile=" + opp.getCardsIn(ZoneType.Exile) + " ai hand=" + ai.getCardsIn(ZoneType.Hand);
        assertTrue(opp.getCardsIn(ZoneType.Exile).anyMatch(c -> c.getName().equals("Serra Angel")), zones);
    }
}
