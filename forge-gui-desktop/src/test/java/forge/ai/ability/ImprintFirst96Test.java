package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 96, Patrilock - Vampire (30/10, forge-v109): Isochron Scepter cast on turn 5 with no instant
// to imprint, empty for the rest of the game
public class ImprintFirst96Test extends AITest {

    private Game setup(final String other) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        for (int i = 0; i < 2; i++) {
            addCard("Island", ai);
        }
        addCardToZone("Isochron Scepter", ai, ZoneType.Hand);
        addCardToZone(other, ai, ZoneType.Hand);
        addCard("Grizzly Bears", opponent);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        ai.setLandsPlayedThisTurn(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        return game;
    }

    @Test
    public void testHoldEmptyScepter() {
        Game game = setup("Grizzly Bears");
        assertEquals(countCardsWithName(game, "Isochron Scepter", ZoneType.Hand), 1, gameStateToString(game));
    }

    @Test
    public void testCastScepterWithInstant() {
        Game game = setup("Counterspell");
        assertEquals(countCardsWithName(game, "Isochron Scepter", ZoneType.Battlefield), 1, gameStateToString(game));
        assertEquals(countCardsWithName(game, "Counterspell", ZoneType.Exile), 1, gameStateToString(game));
    }
}
