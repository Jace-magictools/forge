package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 137 (tournament v21, BlastBurn): the Ravnica karoos asked for a basic land in play, Rakdos
// Carnarium stayed in hand next to Maze of Ith and Dragonskull Summit; any other land can be returned
public class Karoo137Test extends AITest {

    private Game setup(String... battlefield) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        for (String c : battlefield) {
            addCardToZone(c, ai, ZoneType.Battlefield);
        }
        addCardToZone("Rakdos Carnarium", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        return game;
    }

    @Test
    public void testKarooNextToNonBasics() {
        Game game = setup("Maze of Ith", "Dragonskull Summit");
        assertEquals(countCardsWithName(game, "Rakdos Carnarium", ZoneType.Battlefield), 1, gameStateToString(game));
    }

    @Test
    public void testNoKarooOnAnotherKaroo() {
        Game game = setup("Izzet Boilerworks");
        assertEquals(countCardsWithName(game, "Rakdos Carnarium", ZoneType.Hand), 1, gameStateToString(game));
    }
}
