package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 247, D140 (tester 08/11, a test game T2): Marauding Raptor killed the Belligerent
// Yearling cast after it.
public class HarmfulArrival247Test extends AITest {

    private Game setup(int lands, boolean raptorInPlay, String... hand) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        for (int i = 0; i < lands; i++) {
            addCard("Mountain", ai);
        }
        if (raptorInPlay) {
            addCard("Marauding Raptor", ai);
        }
        for (String c : hand) {
            addCardToZone(c, ai, ZoneType.Hand);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        ai.setLandsPlayedThisTurn(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        return game;
    }

    /** the report: both in hand, the Yearling goes first and survives */
    @Test
    public void testYearlingBeforeRaptor() {
        Game game = setup(4, false, "Marauding Raptor", "Belligerent Yearling");
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Belligerent Yearling", ZoneType.Battlefield), 1, gameStateToString(game));
        assertEquals(countCardsWithName(game, "Marauding Raptor", ZoneType.Battlefield), 1, gameStateToString(game));
    }

    /** Raptor already out: the 3/2 stays in hand */
    @Test
    public void testNoSuicideUnderRaptor() {
        Game game = setup(3, true, "Belligerent Yearling");
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Belligerent Yearling", ZoneType.Hand), 1, gameStateToString(game));
    }

    /** a 3/3 survives the 2 damage and is cast */
    @Test
    public void testToughCreatureStillCast() {
        Game game = setup(4, true, "Hill Giant");
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Hill Giant", ZoneType.Battlefield), 1, gameStateToString(game));
    }
}
