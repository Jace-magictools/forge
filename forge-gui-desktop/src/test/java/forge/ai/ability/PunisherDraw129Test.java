package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 129, a punisher draw spell (Risk Factor) targets the opponent who chooses, it is not a gift
// (tournament v18, Burn: Risk Factor 23 times in hand, never cast; Browbeat, which targets a player, was cast)
public class PunisherDraw129Test extends AITest {

    @Test
    public void testRiskFactorCast() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCardToZone("Risk Factor", ai, ZoneType.Hand);
        addCardToZone("Mountain", ai, ZoneType.Hand);
        for (int i = 0; i < 3; i++) {
            addCard("Mountain", ai);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        // opponent's turn: the AI casts its instant at the end of it
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, opponent);
        game.getAction().checkStateEffects(true);
        playUntilNextTurn(game);
        int cast = countCardsWithName(game, "Risk Factor", ZoneType.Graveyard)
                + countCardsWithName(game, "Risk Factor", ZoneType.Stack);
        assertEquals(cast, 1, gameStateToString(game));
    }
}
