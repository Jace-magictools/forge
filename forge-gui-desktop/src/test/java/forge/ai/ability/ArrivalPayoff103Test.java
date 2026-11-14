package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.ai.ArrivalPayoffAi;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

// magic-tools: rule 103 (a tester reports 29/10 18:57 and 19:11, Kavaron - Nothing Gulch): Weapons Manufacturing in hand
// comes down before the artifacts (and the artifact lands) it would turn into Munitions
public class ArrivalPayoff103Test extends AITest {

    private Game setup(int lands, boolean landPlayed, String... hand) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        for (int i = 0; i < lands; i++) {
            addCard("Mountain", ai);
        }
        for (String c : hand) {
            addCardToZone(c, ai, ZoneType.Hand);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        ai.setLandsPlayedThisTurn(landPlayed ? 1 : 0);
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
    public void testCountsArtifactsOnly() {
        Game game = setup(2, true, "Weapons Manufacturing", "Great Furnace", "Mountain");
        Player ai = game.getPlayers().get(1);
        assertEquals(ArrivalPayoffAi.payoffInHand(ai, inHand(ai, "Great Furnace")), inHand(ai, "Weapons Manufacturing"));
        assertNull(ArrivalPayoffAi.payoffInHand(ai, inHand(ai, "Mountain")));
    }

    @Test
    public void testArtifactWaitsForManufacturing() {
        Game game = setup(3, true, "Weapons Manufacturing", "Ornithopter");
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Weapons Manufacturing", ZoneType.Battlefield), 1, gameStateToString(game));
        assertEquals(countCardsWithName(game, "Ornithopter", ZoneType.Battlefield), 1, gameStateToString(game));
        assertEquals(countCardsWithName(game, "Munitions", ZoneType.Battlefield), 1, gameStateToString(game));
    }

    @Test
    public void testArtifactLandAfterManufacturing() {
        Game game = setup(1, false, "Weapons Manufacturing", "Great Furnace", "Mountain");
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Weapons Manufacturing", ZoneType.Battlefield), 1, gameStateToString(game));
        assertEquals(countCardsWithName(game, "Great Furnace", ZoneType.Hand), 1, gameStateToString(game));
        assertTrue(countCardsWithName(game, "Mountain", ZoneType.Battlefield) == 2, gameStateToString(game));
    }
}
