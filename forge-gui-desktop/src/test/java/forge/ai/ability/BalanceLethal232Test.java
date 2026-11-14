package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 232, tournament v24: Dovin, 1 life, no creature against five, Balance in hand from turn 5 until dead
public class BalanceLethal232Test extends AITest {

    private int cast(final int life, final int ours, final int theirs, final int oursInHand) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        ai.setLife(life, null);
        for (int i = 0; i < 2; i++) {
            addCard("Plains", ai);
            addCard("Plains", opponent);
        }
        for (int i = 0; i < ours; i++) {
            addCard("Grizzly Bears", ai);
        }
        for (int i = 0; i < theirs; i++) {
            addCard("Grizzly Bears", opponent);
        }
        addCardToZone("Balance", ai, ZoneType.Hand);
        for (int i = 0; i < oursInHand; i++) {
            addCardToZone("Darksteel Colossus", ai, ZoneType.Hand);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        ai.setLandsPlayedThisTurn(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        return countCardsWithName(game, "Balance", ZoneType.Graveyard);
    }

    @Test
    public void testLethalBoardIgnoresTheHand() {
        // diff 4.5 - 3.5 = 1 once the hand is counted: never cast before the rule
        for (int i = 0; i < 4; i++) {
            assertEquals(cast(4, 0, 3, 6), 1);
        }
    }

    @Test
    public void testClearGainIsNotADraw() {
        // diff 6, life out of reach: 55 % per look before the rule
        for (int i = 0; i < 6; i++) {
            assertEquals(cast(20, 0, 4, 0), 1);
        }
    }

    @Test
    public void testNoGainNoCast() {
        for (int i = 0; i < 3; i++) {
            assertEquals(cast(4, 3, 3, 0), 0);
        }
    }
}
