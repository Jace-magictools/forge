package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

public class PactAiTest extends AITest {

    private Game pactGame(int forests, int pacts) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        for (int i = 0; i < forests; i++) {
            addCard("Forest", ai);
        }
        for (int i = 0; i < pacts; i++) {
            addCardToZone("Summoner's Pact", ai, ZoneType.Hand);
        }
        addCardToZone("Kitchen Finks", ai, ZoneType.Library);
        addCardToZone("Kitchen Finks", ai, ZoneType.Library);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 30);
        // from the AI's second main phase, through the opponent's turn, to the start of the AI's next turn
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        playUntilNextTurn(game);
        playUntilNextTurn(game);
        return game;
    }

    // With the mana to pay for it, the AI uses the Pact to fetch a green creature and pays on upkeep
    @Test
    public void testPactFetchesAndPays() {
        Game game = pactGame(4, 1);
        playUntilPhase(game, PhaseType.DRAW);
        Player ai = game.getRegisteredPlayers().get(1);
        assertFalse(ai.hasLost(), "the AI lost to its own Pact");
        assertEquals(countCardsWithName(game, "Summoner's Pact", ZoneType.Graveyard), 1);
    }

    // Two Pacts but mana for one: the AI must not cast both and lose
    @Test
    public void testTwoPactsDoNotKill() {
        Game game = pactGame(4, 2);
        // before the upkeep: one Pact cast at most (it may cast the second one once the first is paid)
        assertEquals(countCardsWithName(game, "Summoner's Pact", ZoneType.Graveyard), 1);
        playUntilPhase(game, PhaseType.DRAW);
        Player ai = game.getRegisteredPlayers().get(1);
        assertFalse(ai.hasLost(), "the AI lost to its own Pact");
    }

    // Without the mana, the AI keeps the Pact in hand
    @Test
    public void testPactNotCastWithoutMana() {
        Game game = pactGame(3, 1);
        playUntilPhase(game, PhaseType.DRAW);
        Player ai = game.getRegisteredPlayers().get(1);
        assertFalse(ai.hasLost(), "the AI lost to its own Pact");
        assertEquals(countCardsWithName(game, "Summoner's Pact", ZoneType.Hand), 1);
    }
}
