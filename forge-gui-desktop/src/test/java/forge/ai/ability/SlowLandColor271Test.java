package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 271 (a tester report 09/11 12:12, a test game turn 1): with Swamp and Obscura
// Storefront, the one-drops in hand cost {U}: the Swamp casts nothing, the Storefront comes down first
public class SlowLandColor271Test extends AITest {

    private Game setup(String[] battlefield, String... hand) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        for (String c : battlefield) {
            addCard(c, ai);
        }
        for (String c : hand) {
            addCardToZone(c, ai, ZoneType.Hand);
        }
        for (int i = 0; i < 4; i++) {
            addCardToZone("Island", ai, ZoneType.Library);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        return game;
    }

    @Test
    public void testStorefrontWhenTheSwampCastsNothing() {
        Game game = setup(new String[] {}, "Mesmeric Orb", "Obscura Storefront", "Swamp", "Glimpse the Unthinkable",
                "Drown in the Loch", "Ruin Crab", "Jace's Phantasm");
        assertEquals(countCardsWithName(game, "Swamp", ZoneType.Hand), 1, gameStateToString(game));
        assertEquals(countCardsWithName(game, "Obscura Storefront", ZoneType.Hand), 0, gameStateToString(game));
    }

    @Test
    public void testIslandWhenItCastsTheCrab() {
        Game game = setup(new String[] {}, "Mesmeric Orb", "Obscura Storefront", "Island", "Ruin Crab");
        assertEquals(countCardsWithName(game, "Obscura Storefront", ZoneType.Hand), 1, gameStateToString(game));
        assertEquals(countCardsWithName(game, "Ruin Crab", ZoneType.Battlefield), 1, gameStateToString(game));
    }
}
