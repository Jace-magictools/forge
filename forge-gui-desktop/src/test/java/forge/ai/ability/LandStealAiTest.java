package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

public class LandStealAiTest extends AITest {

    // Gilt-Leaf Archdruid (ElfBall's finisher): "Tap seven untapped Druids: gain control of all lands target player controls".
    // ControlGainAi only looked for target cards and never targeted the player: the ability was never activated.
    @Test
    public void testGiltLeafStealsLands() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);

        addCard("Gilt-Leaf Archdruid", ai);
        for (int i = 0; i < 6; i++) {
            addCard("Llanowar Elves", ai);
        }
        addCard("Forest", ai);
        for (int i = 0; i < 4; i++) {
            addCard("Plains", opponent);
        }
        addCard("Grizzly Bears", opponent);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 30);

        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        gameLoopUntilNextPhase(game);

        assertEquals(opponent.getLandsInPlay().size(), 0, "Gilt-Leaf Archdruid did not steal the lands");
        assertEquals(ai.getLandsInPlay().size(), 5, "lands not under the AI's control");
    }

    // with nothing to take, the Druids stay untapped
    @Test
    public void testNoLandsNoActivation() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);

        addCard("Gilt-Leaf Archdruid", ai);
        for (int i = 0; i < 6; i++) {
            addCard("Llanowar Elves", ai);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 30);

        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        gameLoopUntilNextPhase(game);

        assertEquals(ai.getCardsIn(ZoneType.Battlefield).stream().filter(c -> c.isTapped()).count(), 0L,
                "Druids tapped for nothing");
    }
}
