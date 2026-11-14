package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 273 (tester 09/11 13:24, Pod): Kitchen Finks and Viscera Seer in hand, Worldly Tutor at the
// opponent's end step took a second Kitchen Finks; Melira (or Vizier of Remedies) is the missing piece of the loop.
public class MissingPiece273Test extends AITest {

    private String topAfterTutor(String shield) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        addCardToZone("Forest", ai, ZoneType.Battlefield);
        addCardToZone("Worldly Tutor", ai, ZoneType.Hand);
        addCardToZone("Kitchen Finks", ai, ZoneType.Hand);
        addCardToZone("Viscera Seer", ai, ZoneType.Hand);
        addCardToZone("Mountain", opponent, ZoneType.Battlefield);
        fillLibrary(ai, 15);
        addCardToZone("Kitchen Finks", ai, ZoneType.Library);
        addCardToZone(shield, ai, ZoneType.Library);
        addCardToZone("Craw Wurm", ai, ZoneType.Library);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, opponent);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.CLEANUP);
        assertEquals(countCardsWithName(game, "Worldly Tutor", ZoneType.Graveyard), 1, gameStateToString(game));
        return ai.getCardsIn(ZoneType.Library).get(0).getName();
    }

    @Test
    public void testTutorFetchesMelira() {
        assertEquals(topAfterTutor("Melira, Sylvok Outcast"), "Melira, Sylvok Outcast");
    }

    @Test
    public void testTutorFetchesVizier() {
        assertEquals(topAfterTutor("Vizier of Remedies"), "Vizier of Remedies");
    }
}
