package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;

import static org.testng.Assert.assertTrue;

// magic-tools: rule 295 (tournoi v28, Pod contre Ajani, tours 26 a 34 : Melira + Murderous Redcap + Bloodthrone Vampire,
// le Redcap tue un chat par tour au lieu de boucler). Killing the opponent's creatures is progress: the loop goes on,
// clears the board, then goes to the face.
public class LoopOnCreatures295Test extends AITest {

    @Test(timeOut = 120000)
    public void testLoopKeepsGoingThroughCreatures() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        addCard("Melira, Sylvok Outcast", ai).setSickness(false);
        addCard("Murderous Redcap", ai).setSickness(false);
        addCard("Bloodthrone Vampire", ai).setSickness(false);
        for (int i = 0; i < 3; i++) {
            addCard("Grizzly Bears", opponent);
        }
        addCard("Savannah Lions", opponent);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertTrue(opponent.getLife() <= 0 || game.isGameOver(), gameStateToString(game));
    }
}
