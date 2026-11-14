package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertTrue;

// magic-tools: rule 27, a spell's additional discard cost is paid with a spare land
// (Profanation, prof28: Sparkhunter Masticore kept with 4 lands, Condemn and Marsh Flats in hand)
public class DiscardLandCostAiTest extends AITest {

    private Game setUp(int swamps, String... hand) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        for (int i = 0; i < swamps; i++) {
            addCard("Swamp", ai);
        }
        addCardToZone("Sparkhunter Masticore", ai, ZoneType.Hand);
        for (String h : hand) {
            addCardToZone(h, ai, ZoneType.Hand);
        }
        for (int i = 0; i < 10; i++) {
            addCardToZone("Swamp", ai, ZoneType.Library);
        }
        fillLibrary(game.getPlayers().get(0), 20);
        moveToMain2(game, ai);
        return game;
    }

    private static String zones(Player ai) {
        return "battlefield=" + ai.getCardsIn(ZoneType.Battlefield) + " hand=" + ai.getCardsIn(ZoneType.Hand);
    }

    @Test
    public void testMasticoreDiscardsSpareLand() {
        Game game = setUp(3, "Condemn", "Marsh Flats", "Plains");
        Player ai = game.getPlayers().get(1);
        gameLoopUntilNextPhase(game);
        assertTrue(ai.getCardsIn(ZoneType.Battlefield).anyMatch(c -> c.getName().equals("Sparkhunter Masticore")), zones(ai));
        assertTrue(ai.getCardsIn(ZoneType.Hand).anyMatch(c -> c.getName().equals("Condemn")), zones(ai));
    }

    @Test
    public void testMasticoreKeepsLandBelowFour() {
        Game game = setUp(3, "Condemn", "Plains");
        Player ai = game.getPlayers().get(1);
        gameLoopUntilNextPhase(game);
        assertTrue(ai.getCardsIn(ZoneType.Hand).anyMatch(c -> c.getName().equals("Sparkhunter Masticore")), zones(ai));
    }
}
