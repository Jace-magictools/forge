package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 69 (a tester report 29/10, ElfBall - Toxico turn 1): Toxico played Mirrex "for Prosthetic
// Injector" with no creature to equip, Obscura Storefront (a tapped basic later) should come first
public class SlowLand69Test extends AITest {

    private Game setup(String... hand) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        for (String c : hand) {
            addCardToZone(c, ai, ZoneType.Hand);
        }
        for (int i = 0; i < 4; i++) {
            addCardToZone("Plains", ai, ZoneType.Library);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        return game;
    }

    @Test
    public void testStorefrontBeforeMirrex() {
        Game game = setup("Prosthetic Injector", "Elesh Norn, Grand Cenobite", "Obscura Storefront", "Mirrex",
                "Annex Sentry", "Mondrak, Glory Dominus", "Prosthetic Injector");
        assertEquals(countCardsWithName(game, "Mirrex", ZoneType.Hand), 1, gameStateToString(game));
        assertEquals(countCardsWithName(game, "Obscura Storefront", ZoneType.Graveyard), 1, gameStateToString(game));
    }

    @Test
    public void testMirrexForAOneDrop() {
        Game game = setup("Glistener Elf", "Obscura Storefront", "Mirrex", "Mondrak, Glory Dominus");
        assertEquals(countCardsWithName(game, "Mirrex", ZoneType.Battlefield), 1, gameStateToString(game));
        assertEquals(countCardsWithName(game, "Glistener Elf", ZoneType.Battlefield), 1, gameStateToString(game));
    }
}
