package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.CardLists;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

// magic-tools: rule 28, an empty Pump that only carries an opponent target is cast for its
// sub-abilities (Tricolore, tri30: Tithe refused 11 turns, never cast)
public class TargetCarrierAiTest extends AITest {

    @Test
    public void testTitheFetchesPlains() {
        Game game = initAndCreateGame();
        Player opp = game.getPlayers().get(0);
        Player ai = game.getPlayers().get(1);
        addCard("Plains", ai);
        addCardToZone("Tithe", ai, ZoneType.Hand);
        for (int i = 0; i < 10; i++) {
            addCardToZone("Plains", ai, ZoneType.Library);
        }
        for (int i = 0; i < 3; i++) {
            addCard("Mountain", opp);
        }
        fillLibrary(opp, 20);
        moveToMain2(game, ai);
        gameLoopUntilNextPhase(game);
        String zones = "battlefield=" + ai.getCardsIn(ZoneType.Battlefield) + " hand=" + ai.getCardsIn(ZoneType.Hand) + " gy=" + ai.getCardsIn(ZoneType.Graveyard);
        assertTrue(ai.getCardsIn(ZoneType.Graveyard).anyMatch(c -> c.getName().equals("Tithe")), zones);
        // opponent has more lands: two Plains fetched (one may already be played), next to the starting one
        int plains = CardLists.filter(ai.getCardsIn(ZoneType.Hand), c -> c.getName().equals("Plains")).size()
                + CardLists.filter(ai.getCardsIn(ZoneType.Battlefield), c -> c.getName().equals("Plains")).size();
        assertEquals(plains, 3, zones);
    }
}
