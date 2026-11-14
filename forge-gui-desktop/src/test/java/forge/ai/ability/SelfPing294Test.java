package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

// magic-tools: rule 294 (tournoi v28, Pod contre Ajani, tour 13 : Ajani a 1 PV, Murderous Redcap sans marqueur et
// Bloodthrone Vampire en jeu, l'IA ne sacrifie pas). The free outlet counts what the sacrificed creature does itself:
// persist brings the Redcap back as a 1/1 that deals 1 damage.
public class SelfPing294Test extends AITest {

    private Game setup(int oppLife) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        opponent.setLife(oppLife, null);
        addCard("Murderous Redcap", ai).setSickness(false);
        addCard("Bloodthrone Vampire", ai).setSickness(false);
        for (int i = 0; i < 3; i++) {
            addCard("Wall of Stone", opponent);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        return game;
    }

    @Test
    public void testPersistDamageIsLethal() {
        Game game = setup(1);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertTrue(game.getPlayers().get(0).getLife() <= 0 || game.isGameOver(), gameStateToString(game));
    }

    // at 2 the 1/1 coming back falls short: the Redcap stays
    @Test
    public void testNotLethalKeepsRedcap() {
        Game game = setup(2);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(game.getPlayers().get(1).getCardsIn(ZoneType.Graveyard).size(), 0, gameStateToString(game));
        assertEquals(game.getPlayers().get(0).getLife(), 2, gameStateToString(game));
    }
}
