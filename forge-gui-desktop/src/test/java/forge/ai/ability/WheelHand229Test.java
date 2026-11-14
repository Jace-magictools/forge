package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 229, Patrilock (6/11): Wheel of Fortune held T19-T23 with two Perilous Landscape and a Scepter in
// hand, life 18 to 5: lands in hand are not worth keeping, and new cards are better before the first main phase ends
public class WheelHand229Test extends AITest {

    private Game setup(final String other, final PhaseType end) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        for (int i = 0; i < 3; i++) {
            addCard("Mountain", ai);
        }
        addCardToZone("Wheel of Fortune", ai, ZoneType.Hand);
        for (int i = 0; i < 3; i++) {
            addCardToZone(other, ai, ZoneType.Hand);
        }
        addCardToZone("Plains", opponent, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        ai.setLandsPlayedThisTurn(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, end);
        return game;
    }

    @Test
    public void testHandOfLandsWheelsInFirstMain() {
        Game game = setup("Island", PhaseType.COMBAT_BEGIN);
        assertEquals(countCardsWithName(game, "Wheel of Fortune", ZoneType.Graveyard), 1, gameStateToString(game));
    }

    @Test
    public void testHandOfSpellsStays() {
        Game game = setup("Counterspell", PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Wheel of Fortune", ZoneType.Hand), 1, gameStateToString(game));
    }
}
