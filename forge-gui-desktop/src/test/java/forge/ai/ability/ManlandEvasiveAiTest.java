package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;

import static org.testng.Assert.assertEquals;

public class ManlandEvasiveAiTest extends AITest {

    private Game play(final int aiLife, final int oppLife) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        ai.setLife(aiLife, null);
        opponent.setLife(oppLife, null);
        addCard("Creeping Tar Pit", ai).setSickness(false);
        addCards("Swamp", 4, ai);
        addCards("Island", 3, ai);
        addCard("Brineborn Cutthroat", opponent);
        addCard("Baral, Chief of Compliance", opponent);
        addCard("Wan Shi Tong, Librarian", opponent);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 30);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        return game;
    }

    // Solitaire (relevé v275, T22) : Creeping Tar Pit, 8 lands, opponent at 4: 3 unblockable damage were left
    @Test
    public void testUnblockableLandAttacksWhenRacing() {
        assertEquals(play(14, 4).getRegisteredPlayers().get(0).getLife(), 1);
    }

    // and it wins the game outright at 3
    @Test
    public void testUnblockableLandTakesLethal() {
        assertEquals(play(14, 3).getRegisteredPlayers().get(0).getLife(), 0);
    }
}
