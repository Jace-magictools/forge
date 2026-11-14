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

// magic-tools rule 215 (tester 02/11, a test game): the one-drop no longer waits for a payoff whose
// color no source in play or in hand can make, nor for one that needs a land drop we do not have.
public class Headliner215Test extends AITest {

    private Game setup(String[] lands, String... hand) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        for (String l : lands) {
            addCard(l, ai);
        }
        for (String c : hand) {
            addCardToZone(c, ai, ZoneType.Hand);
        }
        fillLibrary(ai, 20);
        fillLibrary(game.getPlayers().get(0), 20);
        ai.setLandsPlayedThisTurn(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        return game;
    }

    private int onBoard(Game game) {
        playUntilPhase(game, PhaseType.END_OF_TURN);
        return countCardsWithName(game, "Stadium Headliner", ZoneType.Battlefield);
    }

    @Test(timeOut = 120000)
    public void testNoBlackSourceNoWait() {
        Game game = setup(new String[] {"Inspiring Vantage"}, "Stadium Headliner", "Elas il-Kor, Sadistic Pilgrim", "Plains");
        assertEquals(onBoard(game), 1, gameStateToString(game));
    }

    @Test(timeOut = 120000)
    public void testBlackLandInHandStillWaits() {
        // two lands + the Swamp next turn: Elas and the Headliner both fit next turn, the Headliner waits
        Game game = setup(new String[] {"Inspiring Vantage", "Plains"}, "Stadium Headliner", "Elas il-Kor, Sadistic Pilgrim", "Swamp");
        assertEquals(onBoard(game), 0, gameStateToString(game));
    }

    @Test(timeOut = 120000)
    public void testOneDropOnTheFirstLandDoesNotWait() {
        // rule 222: one land + the Swamp next turn pay Elas alone, the Headliner comes down now
        Game game = setup(new String[] {"Inspiring Vantage"}, "Stadium Headliner", "Elas il-Kor, Sadistic Pilgrim", "Swamp");
        assertEquals(onBoard(game), 1, gameStateToString(game));
    }

    @Test(timeOut = 120000)
    public void testNoLandDropNoWait() {
        Game game = setup(new String[] {"Godless Shrine"}, "Stadium Headliner", "Elas il-Kor, Sadistic Pilgrim");
        // Godless Shrine alone cannot pay {W}{B} and the hand has no land; the red Headliner is not castable either,
        // so ask the rule directly
        Player ai = game.getPlayers().get(1);
        Card elas = null;
        for (Card c : ai.getCardsIn(ZoneType.Hand)) {
            if (c.getName().startsWith("Elas")) {
                elas = c;
            }
        }
        assertEquals(ArrivalPayoffAi.isNextTurn(ai, elas), false);
    }
}
