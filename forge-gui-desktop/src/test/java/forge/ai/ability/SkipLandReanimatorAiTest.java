package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

// magic-tools: rule 29, no land drop on the first turn when keeping it puts the fatty in the graveyard at cleanup
// (Profanation, tester 26/10: Griselbrand and Exhume in hand on the draw, skip the land, discard Griselbrand;
// only on the first turn, even without the mana for Exhume yet)
public class SkipLandReanimatorAiTest extends AITest {

    private Game setUp(int lands, int turn, int fillers, String... hand) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        for (int i = 0; i < lands; i++) {
            addCard("Swamp", ai);
        }
        addCardToZone("Griselbrand", ai, ZoneType.Hand);
        addCardToZone("Swamp", ai, ZoneType.Hand);
        for (String h : hand) {
            addCardToZone(h, ai, ZoneType.Hand);
        }
        // green cards the swamps cannot cast: they only fill the hand
        for (int i = 0; i < fillers; i++) {
            addCardToZone("Giant Growth", ai, ZoneType.Hand);
        }
        for (int i = 0; i < 10; i++) {
            addCardToZone("Swamp", ai, ZoneType.Library);
        }
        fillLibrary(game.getPlayers().get(0), 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai, turn);
        game.getAction().checkStateEffects(true);
        playUntilNextTurn(game);
        return game;
    }

    private static String zones(Player ai) {
        return "battlefield=" + ai.getCardsIn(ZoneType.Battlefield) + " hand=" + ai.getCardsIn(ZoneType.Hand)
                + " graveyard=" + ai.getCardsIn(ZoneType.Graveyard);
    }

    @Test
    public void testSkipsLandOnFirstTurn() {
        // on the draw, 8 cards: Griselbrand, Swamp, Exhume, 5 Giant Growth, no land in play
        Game game = setUp(0, 2, 5, "Exhume");
        Player ai = game.getPlayers().get(1);
        assertEquals(ai.getLandsInPlay().size(), 0, zones(ai));
        assertTrue(ai.getCardsIn(ZoneType.Graveyard).anyMatch(c -> c.getName().equals("Griselbrand")), zones(ai));
    }

    @Test
    public void testPlaysLandAfterFirstTurn() {
        // same 8 cards on turn 5 with 3 swamps: the land drop is worth more
        Game game = setUp(3, 5, 5, "Exhume");
        Player ai = game.getPlayers().get(1);
        assertEquals(ai.getLandsInPlay().size(), 4, zones(ai));
    }

    @Test
    public void testPlaysLandWithoutReanimation() {
        Game game = setUp(0, 2, 6);
        Player ai = game.getPlayers().get(1);
        assertEquals(ai.getLandsInPlay().size(), 1, zones(ai));
    }

    @Test
    public void testPlaysLandWhenHandFits() {
        // on the play, 7 cards: no discard at cleanup anyway
        Game game = setUp(0, 1, 4, "Exhume");
        Player ai = game.getPlayers().get(1);
        assertEquals(ai.getLandsInPlay().size(), 1, zones(ai));
    }
}
