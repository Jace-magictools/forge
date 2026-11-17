package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertFalse;

/**
 * Rule 318 (D206, a human game 16/11): an evoked creature whose value is a "when you cast this spell" trigger
 * (Nulldrifter draws two) was never evoked, rule 63 only looked at its "enters" triggers.
 */
public class EvokeCastTrigger318Test extends AITest {

    @Test
    public void testEvokeNulldrifterForTheDraw() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard("Island", ai);
        addCard("Island", ai);
        addCard("Island", ai);
        addCard("Forest", opponent);
        addCardToZone("Nulldrifter", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        moveToMain2(game, ai);
        gameLoopUntilNextPhase(game);
        assertFalse(ai.getCardsIn(ZoneType.Hand).anyMatch(c -> c.getName().equals("Nulldrifter")),
                "Nulldrifter kept in hand with three lands and nothing else to do");
    }
}
