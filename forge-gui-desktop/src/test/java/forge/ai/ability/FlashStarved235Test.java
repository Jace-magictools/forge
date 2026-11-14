package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

// magic-tools: rule 235, Enduring Curiosity x2 held from turn 1 to turn 21: the other spells took the mana in main 2.
public class FlashStarved235Test extends AITest {

    /** True if the flash creature is on the battlefield at our end step, after a second main phase with this hand. */
    private boolean castInOurTurn(int islands, String... otherHand) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        for (int i = 0; i < islands; i++) {
            addCard("Island", ai);
        }
        addCardToZone("Enduring Curiosity", ai, ZoneType.Hand);
        for (String c : otherHand) {
            addCardToZone(c, ai, ZoneType.Hand);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        ai.setLandsPlayedThisTurn(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        for (Card c : game.getCardsIn(ZoneType.Battlefield)) {
            if (c.getName().equals("Enduring Curiosity")) {
                return true;
            }
        }
        return false;
    }

    @Test
    public void testCastWhenTheHandWouldTakeItsMana() {
        assertTrue(castInOurTurn(5, "Fog Bank"), "Fog Bank leaves 3 of 5 mana: Curiosity must go first");
    }

    @Test
    public void testWaitsWithNothingElseToCast() {
        assertFalse(castInOurTurn(5), "alone in hand, it waits for the opponent's end step");
    }

    @Test
    public void testWaitsWhenThereIsManaForBoth() {
        assertFalse(castInOurTurn(6, "Fog Bank"), "6 mana: Fog Bank and Curiosity both fit");
    }

    @Test
    public void testAnInstantDoesNotCount() {
        assertFalse(castInOurTurn(5, "Counterspell"), "an instant is not cast in main 2 on its own");
    }
}
