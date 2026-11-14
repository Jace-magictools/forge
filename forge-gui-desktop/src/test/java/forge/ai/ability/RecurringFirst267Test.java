package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 267 (tester 09/11 12:21): Mesmeric Orb (mills every untap) goes before Maddening Cacophony
// (mills once), same cost.
public class RecurringFirst267Test extends AITest {

    @Test
    public void testOrbBeforeMillSorcery() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        addCard("Island", ai);
        addCard("Island", ai);
        addCardToZone("Maddening Cacophony", ai, ZoneType.Hand);
        addCardToZone("Mesmeric Orb", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 30);
        ai.setLandsPlayedThisTurn(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai, 5);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(ai.getCardsIn(ZoneType.Battlefield).anyMatch(c -> c.getName().equals("Mesmeric Orb")), true,
                gameStateToString(game));
        assertEquals(opponent.getCardsIn(ZoneType.Library).size(), 30, gameStateToString(game));
    }
}
