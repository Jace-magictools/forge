package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

public class LoneBattleCry112Test extends AITest {

    // magic-tools rule 112: Signal Pest (0/1, battle cry) attacked alone on turn 3 (tester, 30/10, Gobelin - Nothing Gulch)
    @Test
    public void testSignalPestStaysHomeAlone() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);

        addCard("Mountain", ai);
        Card pest = addCard("Signal Pest", ai);
        pest.setSickness(false);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);

        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai, 3);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);

        assertFalse(pest.isTapped(), "Signal Pest attacked alone\n" + gameStateToString(game));
    }

    // with another attacker, battle cry is worth it: the token hits for 2
    @Test
    public void testSignalPestAttacksWithOthers() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);

        addCard("Mountain", ai);
        addCard("Signal Pest", ai).setSickness(false);
        addCard("Raging Goblin", ai).setSickness(false);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);

        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai, 3);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);

        assertEquals(opponent.getLife(), 18, "Signal Pest + Raging Goblin\n" + gameStateToString(game));
    }
}
