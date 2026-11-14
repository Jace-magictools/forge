package forge.ai.simulation;

import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import forge.card.CardStateName;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;

/** Demande D30 (combos-forge, 31/10) : la copie de recherche garde la face retournée d'une carte non-jeton. */
public class BacksideCopyTest extends SimulationTest {
    @Test
    public void testCopyKeepsBackside() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        Card delver = addCard("Delver of Secrets", p);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, p);
        delver.setBackSide(true);
        delver.changeToState(CardStateName.Backside);
        game.getAction().checkStateEffects(true);
        AssertJUnit.assertEquals("Insectile Aberration", delver.getName());

        GameCopier copier = new GameCopier(game);
        Game copy = copier.makeCopy();
        Card c = (Card) copier.find(delver);
        AssertJUnit.assertTrue(c.isBackSide());
        AssertJUnit.assertEquals(CardStateName.Backside, c.getCurrentStateName());
        AssertJUnit.assertEquals("Insectile Aberration", c.getName());
        AssertJUnit.assertTrue(c.hasKeyword(forge.game.keyword.Keyword.FLYING));
        copy.getAction().checkStateEffects(true);
        AssertJUnit.assertEquals("Insectile Aberration", c.getName());
    }
}
