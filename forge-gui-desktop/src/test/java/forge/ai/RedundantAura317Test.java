package forge.ai;

import org.testng.annotations.Test;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

// magic-tools rule 317 (D197, plateau 15/11, a tester): a second Unable to Scream on a creature already 0/2
// without abilities under the first one.
public class RedundantAura317Test extends AITest {

    private Game game(boolean otherCreature) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard("Island", ai);
        addCard("Island", ai);
        addCardToZone("Unable to Scream", ai, ZoneType.Hand);
        Card angel = addCard("Serra Angel", opponent);
        Card first = addCard("Unable to Scream", ai);
        first.attachToEntity(angel, null);
        if (otherCreature) {
            addCard("Hill Giant", opponent);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        ai.addLandPlayedThisTurn();
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        return game;
    }

    private static long screamsOn(Player opp, String name) {
        for (Card c : opp.getCreaturesInPlay()) {
            if (c.getName().equals(name)) {
                return c.getAttachedCards().stream().filter(a -> a.getName().equals("Unable to Scream")).count();
            }
        }
        return -1;
    }

    @Test(timeOut = 120000)
    public void testOnlyEnchantedCreatureKeepsAura() {
        Game game = game(false);
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(screamsOn(opponent, "Serra Angel"), 1L, "second Unable to Scream on the same creature");
        assertEquals(ai.getCardsIn(ZoneType.Hand).size(), 1, "aura should stay in hand");
    }

    @Test(timeOut = 120000)
    public void testOtherCreatureGetsAura() {
        Game game = game(true);
        Player opponent = game.getPlayers().get(0);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(screamsOn(opponent, "Serra Angel"), 1L, "second Unable to Scream on the same creature");
        assertTrue(screamsOn(opponent, "Hill Giant") <= 1L);
    }
}
