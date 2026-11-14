package forge.ai.ability;

import org.testng.annotations.Test;

import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import forge.ai.AITest;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

// magic-tools: rule 26 (tester), Fireball 1+1 kills two small creatures when X allows it,
// and is never split into shares that kill nothing
public class DividedBurnAiTest extends AITest {

    private Game play(int mountains, String... creatures) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        addCards("Mountain", mountains, ai);
        addCardToZone("Fireball", ai, ZoneType.Hand);
        for (String c : creatures) {
            addCard(c, opponent);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai, 21);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        return game;
    }

    @Test
    public void testSplitKillsTwoSmallCreatures() {
        // 4 Mountains: X=3 on one target, or X=2 + {1} split 1+1 on two 1-toughness creatures
        Game game = play(4, "Savannah Lions", "Llanowar Elves");
        Player opponent = game.getPlayers().get(0);
        assertEquals(opponent.getCreaturesInPlay().size(), 0,
                "Fireball not split 1+1 on two 1-toughness creatures\n" + gameStateToString(game));
    }

    @Test
    public void testNoSplitIntoZero() {
        // 3 Mountains: X=1 split would deal 0 to each, kill the Bears instead
        Game game = play(3, "Savannah Lions", "Grizzly Bears");
        Player opponent = game.getPlayers().get(0);
        assertTrue(opponent.getCreaturesInPlay().size() == 1 && opponent.getCreaturesInPlay().get(0).getName().equals("Savannah Lions"),
                "Fireball split into nothing or wasted\n" + gameStateToString(game));
    }
}
