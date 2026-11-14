package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 89 (a tester report 29/10 12:26, ElfBall - Snowboard turn 3): Snowboard played Shimmerdrift Vale
// tapped with one Swamp in play; the untapped Island would have paid {2} to foretell Vengeful Reaper
public class ForetellLand89Test extends AITest {

    private Game setup(String... hand) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        addCardToZone("Snow-Covered Swamp", ai, ZoneType.Battlefield);
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
    public void testUntappedLandToForetell() {
        Game game = setup("Vega, the Watcher", "Vengeful Reaper", "Shimmerdrift Vale", "Snow-Covered Island",
                "Vengeful Reaper");
        assertEquals(countCardsWithName(game, "Snow-Covered Island", ZoneType.Battlefield), 1, gameStateToString(game));
        assertEquals(game.getPlayers().get(1).getCardsIn(ZoneType.Exile).size(), 1, gameStateToString(game));
        assertEquals(countCardsWithName(game, "Vengeful Reaper", ZoneType.Hand), 1, gameStateToString(game));
    }

    @Test
    public void testTappedLandWithoutForetell() {
        Game game = setup("Vega, the Watcher", "Shimmerdrift Vale", "Snow-Covered Island", "Sengir Vampire");
        assertEquals(countCardsWithName(game, "Shimmerdrift Vale", ZoneType.Battlefield), 1, gameStateToString(game));
    }
}
