package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 95, Patrilock - Vampire (30/10, forge-v109): Memory Jar in play from turn 13, empty hand,
// six lands, never activated (also a tester report: "Memory Jar, the AI never uses it")
public class MemoryJar95Test extends AITest {

    private Game setup(final int handSize, final boolean opponentsTurn, final PhaseType start, final PhaseType end) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        for (int i = 0; i < 6; i++) {
            addCard("Island", ai);
        }
        addCard("Memory Jar", ai);
        for (int i = 0; i < handSize; i++) {
            addCardToZone("Island", ai, ZoneType.Hand);
        }
        addCard("Grizzly Bears", opponent);
        for (int i = 0; i < 4; i++) {
            addCardToZone("Plains", opponent, ZoneType.Hand);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        ai.setLandsPlayedThisTurn(1);
        game.getPhaseHandler().devModeSet(start, opponentsTurn ? opponent : ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, end);
        return game;
    }

    // magic-tools: rule 228, the hand comes back and the "next end step" is ours: any hand size, at the opponent's end step
    @Test
    public void testWheelWithEmptyHand() {
        Game game = setup(0, true, PhaseType.END_OF_TURN, PhaseType.UPKEEP);
        assertEquals(countCardsWithName(game, "Memory Jar", ZoneType.Graveyard), 1, gameStateToString(game));
    }

    @Test
    public void testFullHandComesBackAtOurEndStep() {
        Game game = setup(6, true, PhaseType.END_OF_TURN, PhaseType.MAIN2);
        Player ai = game.getPlayers().get(1);
        assertEquals(countCardsWithName(game, "Memory Jar", ZoneType.Graveyard), 1, gameStateToString(game));
        assertEquals(ai.getCardsIn(ZoneType.Exile).size(), 6, gameStateToString(game));
    }

    @Test
    public void testNotOnOurOwnTurn() {
        Game game = setup(0, false, PhaseType.MAIN1, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Memory Jar", ZoneType.Battlefield), 1, gameStateToString(game));
    }

    @Test
    public void testNotDuringOpponentsMainPhase() {
        Game game = setup(0, true, PhaseType.MAIN1, PhaseType.COMBAT_END);
        assertEquals(countCardsWithName(game, "Memory Jar", ZoneType.Battlefield), 1, gameStateToString(game));
    }
}
