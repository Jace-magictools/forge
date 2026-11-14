package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 86 (a tester report 29/10 15:25, Eldratron - Gwyll turn 1): Island played with Urza's Tower and
// Urza's Mine in hand; with two different Urza lands the Tron pieces go down first
public class Tron86Test extends AITest {

    private Game setup(boolean powerPlantInLibrary, String... hand) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        for (String c : hand) {
            addCardToZone(c, ai, ZoneType.Hand);
        }
        fillLibrary(ai, 20);
        if (powerPlantInLibrary) {
            addCardToZone("Urza's Power Plant", ai, ZoneType.Library);
        }
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        return game;
    }

    @Test
    public void testMineBeforeIsland() {
        Game game = setup(true, "Island", "Urza's Tower", "Urza's Mine", "Spell Burst", "Trinket Mage", "Culling Scales");
        assertEquals(countCardsWithName(game, "Island", ZoneType.Hand), 1, gameStateToString(game));
    }

    @Test
    public void testNoTrioLeftPlaysIsland() {
        Game game = setup(false, "Island", "Urza's Tower", "Urza's Mine", "Spell Burst", "Trinket Mage", "Culling Scales");
        assertEquals(countCardsWithName(game, "Island", ZoneType.Battlefield), 1, gameStateToString(game));
    }
}
