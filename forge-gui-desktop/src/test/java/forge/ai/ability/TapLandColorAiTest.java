package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 21, an untapped land is played first only if it brings the color of a spell it makes castable
// (stock AI played Plains "to cast Birds of Paradise" and the tri-lands came down tapped later)
public class TapLandColorAiTest extends AITest {

    private Game turnOne(String... hand) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        for (String c : hand) {
            addCardToZone(c, ai, ZoneType.Hand);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        return game;
    }

    @Test
    public void testTapLandWhenUntappedLandLacksTheColor() {
        Game game = turnOne("Plains", "Jungle Shrine", "Birds of Paradise");
        assertEquals(countCardsWithName(game, "Jungle Shrine", ZoneType.Battlefield), 1, gameStateToString(game));
        assertEquals(countCardsWithName(game, "Plains", ZoneType.Battlefield), 0, gameStateToString(game));
    }

    // the untapped land brings the color: play it and cast the one-drop
    @Test
    public void testUntappedLandWithTheColor() {
        Game game = turnOne("Forest", "Jungle Shrine", "Birds of Paradise");
        assertEquals(countCardsWithName(game, "Forest", ZoneType.Battlefield), 1, gameStateToString(game));
        assertEquals(countCardsWithName(game, "Birds of Paradise", ZoneType.Battlefield), 1, gameStateToString(game));
    }
}
