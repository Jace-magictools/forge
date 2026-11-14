package forge.ai.ability;

import java.util.Arrays;
import java.util.List;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.ai.AiAttackController;
import forge.ai.AiBlockController;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.combat.Combat;
import forge.game.player.Player;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 303 (tester 12/11, D171 point 1): Troll des Cavernes (5/6, "can't block unless at least two
// other creatures block") was the human's only creature; the AI's attack evaluation counted it as a blocker and its
// block simulation had it block alone, so two 5/4 stayed home. Same keyword here: Orcish Conscripts; and Mogg Flunkies
// (can't block alone). The prediction must leave them out when too few creatures can block.
public class BlockAlone303Test extends AITest {

    private int simulatedBlockers(final String blocker, final int others) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opp = game.getPlayers().get(0);
        Card a1 = addCard("Craw Wurm", ai);
        Card a2 = addCard("Craw Wurm", ai);
        addCard(blocker, opp);
        for (int k = 0; k < others; k++) {
            addCard("Grizzly Bears", opp);
        }
        Combat combat = new Combat(ai);
        combat.addAttacker(a1, opp);
        combat.addAttacker(a2, opp);
        new AiBlockController(opp, opp).assignBlockersForCombat(combat);
        return combat.getAllBlockers().stream().filter(c -> c.getName().equals(blocker)).toList().size();
    }

    private int possibleBlockers(final String blocker, final int others) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opp = game.getPlayers().get(0);
        Card a1 = addCard("Craw Wurm", ai);
        Card b = addCard(blocker, opp);
        for (int k = 0; k < others; k++) {
            addCard("Grizzly Bears", opp);
        }
        List<Card> list = AiAttackController.getPossibleBlockers(opp.getCreaturesInPlay(), Arrays.asList(a1), false);
        return list.contains(b) ? 1 : 0;
    }

    @Test
    public void testTwoOthersNeededAlone() {
        assertEquals(possibleBlockers("Orcish Conscripts", 0), 0);
        assertEquals(simulatedBlockers("Orcish Conscripts", 0), 0);
    }

    @Test
    public void testCantBlockAloneAlone() {
        assertEquals(possibleBlockers("Mogg Flunkies", 0), 0);
    }

    // enough company: the restriction is met, the creature stays a possible blocker
    @Test
    public void testEnoughOthers() {
        assertEquals(possibleBlockers("Orcish Conscripts", 2), 1);
        assertEquals(possibleBlockers("Mogg Flunkies", 1), 1);
        assertEquals(possibleBlockers("Orcish Conscripts", 1), 0);
    }
}
