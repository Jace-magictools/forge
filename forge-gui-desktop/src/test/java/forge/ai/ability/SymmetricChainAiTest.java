package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

// magic-tools: rule 30, "each player loses, discards, sacrifices" chains judged as a whole
// (Pox, pox32: Smallpox refused 57 turns in 8 games, the creature part alone refused it)
public class SymmetricChainAiTest extends AITest {

    @Test
    public void testSmallpoxCastWhenOpponentLosesMore() {
        Game game = initAndCreateGame();
        Player opp = game.getPlayers().get(0);
        Player ai = game.getPlayers().get(1);
        for (int i = 0; i < 4; i++) {
            addCard("Swamp", ai);
        }
        addCardToZone("Smallpox", ai, ZoneType.Hand);
        for (int i = 0; i < 3; i++) {
            addCard("Forest", opp);
        }
        addCard("Grizzly Bears", opp);
        addCardToZone("Llanowar Elves", opp, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opp, 20);
        moveToMain2(game, ai);
        gameLoopUntilNextPhase(game);
        String zones = "ai battlefield=" + ai.getCardsIn(ZoneType.Battlefield) + " hand=" + ai.getCardsIn(ZoneType.Hand)
                + " opp battlefield=" + opp.getCardsIn(ZoneType.Battlefield);
        assertTrue(ai.getCardsIn(ZoneType.Graveyard).anyMatch(c -> c.getName().equals("Smallpox")), zones);
        assertFalse(opp.getCardsIn(ZoneType.Battlefield).anyMatch(c -> c.getName().equals("Grizzly Bears")), zones);
    }

    @Test
    public void testSmallpoxKeptWhenWeLoseMore() {
        Game game = initAndCreateGame();
        Player opp = game.getPlayers().get(0);
        Player ai = game.getPlayers().get(1);
        for (int i = 0; i < 4; i++) {
            addCard("Swamp", ai);
        }
        addCard("Grizzly Bears", ai);
        addCardToZone("Smallpox", ai, ZoneType.Hand);
        addCardToZone("Hill Giant", ai, ZoneType.Hand);
        for (int i = 0; i < 6; i++) {
            addCard("Forest", opp);
        }
        fillLibrary(ai, 20);
        fillLibrary(opp, 20);
        moveToMain2(game, ai);
        gameLoopUntilNextPhase(game);
        String zones = "ai battlefield=" + ai.getCardsIn(ZoneType.Battlefield) + " hand=" + ai.getCardsIn(ZoneType.Hand);
        assertTrue(ai.getCardsIn(ZoneType.Hand).anyMatch(c -> c.getName().equals("Smallpox")), zones);
    }
}
