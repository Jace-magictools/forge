package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 297 (merfolk, v335 report): Eclipsed Merrow, Sygg, Meanders Guide stayed in hand turn after turn
// to keep Hindering Light open, a counter that only protects what we control.
public class ProtectiveCounter297Test extends AITest {

    private Game setup(String... hand) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        addCard("Island", ai);
        addCard("Island", ai);
        addCard("Plains", ai);
        addCard("Merfolk of the Pearl Trident", ai); // a creature in play: rule 174 (deploy first) stays out
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

    /** Hindering Light does not hold the creature back */
    @Test
    public void testCreatureBeforeHinderingLight() {
        Game game = setup("Coral Merfolk", "Hindering Light");
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Coral Merfolk", ZoneType.Battlefield), 1, gameStateToString(game));
    }

    /** a real counterspell still does (rule 22 unchanged) */
    @Test
    public void testCounterspellStillHolds() {
        Game game = setup("Coral Merfolk", "Counterspell");
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Coral Merfolk", ZoneType.Battlefield), 0, gameStateToString(game));
    }
}
