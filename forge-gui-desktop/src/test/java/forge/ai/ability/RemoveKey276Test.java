package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.ai.ComputerUtilCard;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.player.Player;

import static org.testng.Assert.assertEquals;

// magic-tools rule 276 (tester 10/11 08:26, « le plan à chaque tour » : le deck adverse). The removal goes first on a
// key card of the opponent's plan (Melira, a combo piece) rather than on the bigger vanilla body (Craw Wurm).
public class RemoveKey276Test extends AITest {

    private Card target(boolean plan) {
        return target(plan, true);
    }

    private Card target(boolean plan, boolean combo) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        if (plan) {
            opponent.getRegisteredPlayer().getDeck().addKeyCard("Melira, Sylvok Outcast");
            if (combo) {
                opponent.getRegisteredPlayer().getDeck().setAiHints("Combo$Melira, Sylvok Outcast;Kitchen Finks;Viscera Seer");
            }
        }
        Card melira = addCard("Melira, Sylvok Outcast", opponent);
        Card wurm = addCard("Craw Wurm", opponent);
        game.getAction().checkStateEffects(true);
        CardCollection list = new CardCollection();
        list.add(wurm);
        list.add(melira);
        return ComputerUtilCard.getBestRemovalTargetAI(ai, list);
    }

    @Test(timeOut = 300000)
    public void testRemovesKeyFirst() {
        assertEquals(target(true).getName(), "Melira, Sylvok Outcast", "removal aimed at the opponent's key card");
    }

    @Test(timeOut = 300000)
    public void testKeyOutsideComboBiggest() {
        assertEquals(target(true, false).getName(), "Craw Wurm", "a key card outside any combo gets no bonus");
    }

    @Test(timeOut = 300000)
    public void testWithoutPlanBiggest() {
        assertEquals(target(false).getName(), "Craw Wurm", "without a plan the bigger body goes first");
    }
}
