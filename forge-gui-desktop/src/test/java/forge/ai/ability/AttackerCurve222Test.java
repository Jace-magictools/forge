package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 222, Inti stayed in hand with two free lands for Warleader's Call next turn (Bunny Burrow - eowyn, t3)
public class AttackerCurve222Test extends AITest {

    private int onBoard(String creature, int lands) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        for (int i = 0; i < lands; i++) {
            addCard(i % 2 == 0 ? "Mountain" : "Plains", ai);
        }
        addCardToZone(creature, ai, ZoneType.Hand);
        addCardToZone("Warleader's Call", ai, ZoneType.Hand);
        addCardToZone("Plains", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(game.getPlayers().get(0), 20);
        ai.setLandsPlayedThisTurn(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        return countCardsWithName(game, creature, ZoneType.Battlefield);
    }

    @Test(timeOut = 120000)
    public void testTwoDropComesDown() {
        assertEquals(onBoard("Inti, Seneschal of the Sun", 2), 1);
    }

    @Test(timeOut = 120000)
    public void testNoAttackerStillWaits() {
        assertEquals(onBoard("Steel Wall", 2), 0);
    }

    @Test(timeOut = 120000)
    public void testPayoffPayableNowStillFirst() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        addCard("Mountain", ai);
        addCard("Plains", ai);
        addCard("Mountain", ai);
        addCardToZone("Inti, Seneschal of the Sun", ai, ZoneType.Hand);
        addCardToZone("Warleader's Call", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(game.getPlayers().get(0), 20);
        ai.setLandsPlayedThisTurn(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Warleader's Call", ZoneType.Battlefield), 1);
        assertEquals(countCardsWithName(game, "Inti, Seneschal of the Sun", ZoneType.Battlefield), 0);
    }
}
