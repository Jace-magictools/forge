package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.ai.LandfallFirst259;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

// magic-tools rule 259 (D147 point 5, tester 08/11, a test game step 125): the land went down in main 1
// and Ruin Crab in main 2, its landfall trigger lost. The crab comes first, the land after it: the opponent mills
// three. Control: no land left to play this turn, nothing is offered before the land drop.
public class LandfallFirst259Test extends AITest {

    private Game setUp(boolean landInHand) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCards("Island", 2, ai);
        addCardToZone("Ruin Crab", ai, ZoneType.Hand);
        if (landInHand) {
            addCardToZone("Island", ai, ZoneType.Hand);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        return game;
    }

    @Test(timeOut = 120000)
    public void testCrabBeforeLand() {
        Game game = setUp(true);
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        assertEquals(LandfallFirst259.beforeLand(ai).size(), 1, "crab offered before the land");
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Ruin Crab", ZoneType.Battlefield), 1, "crab cast");
        assertTrue(opponent.getCardsIn(ZoneType.Graveyard).size() >= 3, "landfall milled, graveyard "
                + opponent.getCardsIn(ZoneType.Graveyard).size());
    }

    @Test(timeOut = 120000)
    public void testNoLandNothingOffered() {
        Game game = setUp(false);
        assertTrue(LandfallFirst259.beforeLand(game.getPlayers().get(1)).isEmpty(), "no land drop to count");
    }
}
