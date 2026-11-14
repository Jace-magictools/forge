package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.CounterType;
import forge.game.phase.PhaseType;
import forge.game.player.Player;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 309 (Sauron, The One Ring): at 6 life with two burden counters and a 3/3 across, a third counter
// means 3 life lost at our next upkeep after taking 3: dead. Without the 3/3, drawing is fine.
public class RecurringLoss309Test extends AITest {

    private int burdenAfterTurn(final boolean attacker) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        ai.setLife(6, null);
        Card ring = addCard("The One Ring", ai);
        ring.setCounters(CounterType.getType("BURDEN"), 2);
        if (attacker) {
            addCard("Hill Giant", opponent).setSickness(false);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.CLEANUP);
        return ring.getCounters(CounterType.getType("BURDEN"));
    }

    @Test
    public void testNoBurdenWhenItKills() {
        assertEquals(burdenAfterTurn(true), 2);
    }

    @Test
    public void testBurdenWhenSafe() {
        assertEquals(burdenAfterTurn(false), 3);
    }
}
