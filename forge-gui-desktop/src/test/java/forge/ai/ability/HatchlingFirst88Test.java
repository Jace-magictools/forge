package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.CounterEnumType;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

// magic-tools: a tester report 29/10 16:01 (Gwyll - Vampire, turn 13): five lands, Tithe Drinker in play, Voracious
// Hatchling and Edge of the Divinity in hand. The AI cast Edge (and paid extort) and kept the Hatchling; the
// Hatchling first, then Edge (white and black) removes two of its -1/-1 counters
public class HatchlingFirst88Test extends AITest {

    @Test
    public void testHatchlingBeforeEdge() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        addCard("Plains", ai);
        addCard("Plains", ai);
        addCard("Swamp", ai);
        addCard("Swamp", ai);
        addCard("Shattered Sanctum", ai);
        addCard("Tithe Drinker", ai);
        addCardToZone("Voracious Hatchling", ai, ZoneType.Hand);
        addCardToZone("Edge of the Divinity", ai, ZoneType.Hand);
        addCard("Vampire of the Dire Moon", opponent);
        addCard("Grizzly Bears", opponent);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        ai.setLandsPlayedThisTurn(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Voracious Hatchling", ZoneType.Battlefield), 1, gameStateToString(game));
        Card h = null;
        for (Card c : ai.getCardsIn(ZoneType.Battlefield)) {
            if (c.getName().equals("Voracious Hatchling")) {
                h = c;
            }
        }
        assertTrue(h.getCounters(CounterEnumType.M1M1) < 4, gameStateToString(game));
    }

    @Test
    public void testExtortPaidWhenNothingWaits() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        addCard("Plains", ai);
        addCard("Swamp", ai);
        addCard("Swamp", ai);
        addCard("Tithe Drinker", ai);
        addCardToZone("Edge of the Divinity", ai, ZoneType.Hand);
        addCard("Grizzly Bears", opponent);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        ai.setLandsPlayedThisTurn(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.COMBAT_BEGIN);
        assertEquals(countCardsWithName(game, "Edge of the Divinity", ZoneType.Battlefield), 1, gameStateToString(game));
        assertEquals(opponent.getLife(), 19, gameStateToString(game));
    }
}
