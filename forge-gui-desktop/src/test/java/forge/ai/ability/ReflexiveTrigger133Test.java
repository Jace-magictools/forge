package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 133, a reflexive "when you do" trigger aimed at the opponent does not veto its spell
// (tournament v19, Sauron: Foray of Orcs 36 times in hand, never cast; X = the Army's power is 0 before resolution)
public class ReflexiveTrigger133Test extends AITest {

    @Test
    public void testForayOfOrcsCast() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCardToZone("Foray of Orcs", ai, ZoneType.Hand);
        for (int i = 0; i < 4; i++) {
            addCard("Mountain", ai);
        }
        addCard("Grizzly Bears", opponent);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai, 5);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Foray of Orcs", ZoneType.Graveyard), 1, gameStateToString(game));
        assertEquals(countCardsWithName(game, "Grizzly Bears", ZoneType.Graveyard), 1, gameStateToString(game));
    }
}
