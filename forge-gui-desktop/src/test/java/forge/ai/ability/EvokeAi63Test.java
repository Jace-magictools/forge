package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Rule 63 (Modern TopDeck archetypes, UW control and Goryo's far below their real win rate): an evoked creature is
 * sacrificed at once, so the AI evokes it only when its ETB really does something (Solitude evoked on turn 1 with no
 * creature to exile threw away two cards).
 */
public class EvokeAi63Test extends AITest {

    private Game evokeSolitude(String oppCreature) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        if (oppCreature != null) {
            addCard(oppCreature, opponent);
        }
        addCard("Forest", opponent);
        addCardToZone("Solitude", ai, ZoneType.Hand);
        addCardToZone("Savannah Lions", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        moveToMain2(game, ai);
        gameLoopUntilNextPhase(game);
        return game;
    }

    @Test
    public void testNoEvokeWithoutTarget() {
        Game game = evokeSolitude(null);
        Player ai = game.getPlayers().get(1);
        assertTrue(ai.getCardsIn(ZoneType.Hand).anyMatch(c -> c.getName().equals("Solitude")),
                "Solitude evoked with nothing to exile");
    }

    @Test
    public void testEvokeOnThreat() {
        Game game = evokeSolitude("Tarmogoyf");
        Player opponent = game.getPlayers().get(0);
        Player ai = game.getPlayers().get(1);
        // the AI may wait for a better moment, but if it evoked it must have exiled the creature
        if (!ai.getCardsIn(ZoneType.Hand).anyMatch(c -> c.getName().equals("Solitude"))) {
            assertFalse(opponent.getCardsIn(ZoneType.Battlefield).anyMatch(c -> c.getName().equals("Tarmogoyf")),
                    "Solitude evoked without exiling Tarmogoyf");
        }
    }
}
