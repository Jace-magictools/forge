package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: a tester report 29/10 12:37 (ElfBall - Snowboard, turn 6): Glimpse of Nature in hand with Llanowar Elves
// and Arbor Elf; Forge never cast Glimpse (Effect spell without AILogic). Glimpse first, then the elves draw cards
public class CastBoon90Test extends AITest {

    @Test
    public void testGlimpseBeforeElves() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        addCard("Forest", ai);
        addCard("Forest", ai);
        addCard("Forest", ai);
        addCardToZone("Glimpse of Nature", ai, ZoneType.Hand);
        addCardToZone("Llanowar Elves", ai, ZoneType.Hand);
        addCardToZone("Arbor Elf", ai, ZoneType.Hand);
        addCard("Grizzly Bears", opponent);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        ai.setLandsPlayedThisTurn(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Glimpse of Nature", ZoneType.Graveyard), 1, gameStateToString(game));
        assertEquals(ai.getNumDrawnThisTurn(), 2, gameStateToString(game));
    }

    @Test
    public void testGlimpseKeptWithOneElf() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        addCard("Forest", ai);
        addCard("Forest", ai);
        addCardToZone("Glimpse of Nature", ai, ZoneType.Hand);
        addCardToZone("Llanowar Elves", ai, ZoneType.Hand);
        addCard("Grizzly Bears", opponent);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        ai.setLandsPlayedThisTurn(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Glimpse of Nature", ZoneType.Hand), 1, gameStateToString(game));
    }
}
