package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

// magic-tools: rule 87 (a tester report 29/10 15:11, Chevalier - Gwyll): with two mana, Worthy Knight ("whenever you
// cast a Knight spell, create a 1/1") goes before Rimrock Knight, which would feed it next turn
public class CastEngine87Test extends AITest {

    private Game setup(String other) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        addCard("Plains", ai);
        addCard("Mountain", ai);
        addCardToZone("Rimrock Knight", ai, ZoneType.Hand);
        addCardToZone("Worthy Knight", ai, ZoneType.Hand);
        if (other != null) {
            addCardToZone(other, ai, ZoneType.Hand);
        }
        addCard("Grizzly Bears", opponent);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        ai.setLandsPlayedThisTurn(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        return game;
    }

    private Card inHand(Player ai, String name) {
        for (Card c : ai.getCardsIn(ZoneType.Hand)) {
            if (c.getName().equals(name)) {
                return c;
            }
        }
        return null;
    }

    @Test
    public void testWorthyKnightFirst() {
        Game game = setup(null);
        Player ai = game.getPlayers().get(1);
        assertTrue(forge.ai.EngineFirstAi.fedFromHand(ai, inHand(ai, "Worthy Knight")));
        assertFalse(forge.ai.EngineFirstAi.fedFromHand(ai, inHand(ai, "Rimrock Knight")));
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Worthy Knight", ZoneType.Battlefield), 1, gameStateToString(game));
        assertEquals(countCardsWithName(game, "Rimrock Knight", ZoneType.Battlefield), 0, gameStateToString(game));
    }

    @Test
    public void testNoFuelNoEngine() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        addCardToZone("Worthy Knight", ai, ZoneType.Hand);
        addCardToZone("Grizzly Bears", ai, ZoneType.Hand);
        assertFalse(forge.ai.EngineFirstAi.fedFromHand(ai, inHand(ai, "Worthy Knight")));
    }
}
