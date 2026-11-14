package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.ai.LandSearchAi;
import forge.game.Game;
import forge.game.card.CardCollection;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;

// magic-tools: rule 246, tester 08/11 (a test game T2): Expedition Map took an Island over Urza's Mine.
public class LandSearch246Test extends AITest {

    private CardCollection library(Player ai, String... names) {
        CardCollection lands = new CardCollection();
        for (String n : names) {
            lands.add(addCardToZone(n, ai, ZoneType.Library));
        }
        return lands;
    }

    /** The report: Island and Power Plant out, blue already made, a Tron piece beats a second Island */
    @Test
    public void testTronPieceOverUselessBasic() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        addCard("Island", ai);
        addCard("Urza's Power Plant", ai);
        addCardToZone("Trinket Mage", ai, ZoneType.Hand);
        addCardToZone("Trinket Mage", ai, ZoneType.Hand);
        CardCollection lands = library(ai, "Island", "Island", "Island", "Urza's Mine", "Urza's Tower");
        assertNotEquals(LandSearchAi.choose(ai, lands, false).getName(), "Island");
    }

    /** Tower in hand, Power Plant out: the Mine completes the set */
    @Test
    public void testCompletesTheSet() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        addCard("Island", ai);
        addCard("Urza's Power Plant", ai);
        addCardToZone("Urza's Tower", ai, ZoneType.Hand);
        CardCollection lands = library(ai, "Island", "Urza's Tower", "Urza's Mine", "Urza's Power Plant");
        assertEquals(LandSearchAi.choose(ai, lands, false).getName(), "Urza's Mine");
    }

    /** Equal pieces: the one with the fewest copies left in the library */
    @Test
    public void testRarestOnTie() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        addCard("Urza's Power Plant", ai);
        CardCollection lands = library(ai, "Urza's Tower", "Urza's Tower", "Urza's Tower", "Urza's Mine");
        assertEquals(LandSearchAi.choose(ai, lands, false).getName(), "Urza's Mine");
    }

    /** The color the hand lacks still beats the set bonus of one partner */
    @Test
    public void testMissingColorStillFirst() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        addCard("Island", ai);
        addCard("Urza's Power Plant", ai);
        addCardToZone("Lightning Bolt", ai, ZoneType.Hand);
        CardCollection lands = library(ai, "Mountain", "Urza's Mine");
        assertEquals(LandSearchAi.choose(ai, lands, false).getName(), "Mountain");
    }
}
