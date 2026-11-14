package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

public class MorphAiTest extends AITest {

    // Birchlore Rangers costs {G} face up: casting it face down for {3} only loses its mana ability
    @Test
    public void testCheapMorphCastFaceUp() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);

        addCard("Forest", ai);
        addCard("Forest", ai);
        addCard("Forest", ai);
        addCardToZone("Birchlore Rangers", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 30);

        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        gameLoopUntilNextPhase(game);

        assertEquals(ai.getCreaturesInPlay().size(), 1);
        for (Card c : ai.getCreaturesInPlay()) {
            assertFalse(c.isFaceDown(), "Birchlore Rangers cast face down");
        }
    }
}
