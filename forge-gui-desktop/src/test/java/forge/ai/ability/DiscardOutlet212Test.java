package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.ai.DiscardOutletAi;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

// magic-tools rule 212 (tester 03/11, a test game): a cycling card in the graveyard is no reason to keep
// the land drop in hand; an ability really usable with a discard cost still is.
public class DiscardOutlet212Test extends AITest {

    private SpellAbility withDiscard(Card c) {
        for (SpellAbility sa : c.getSpellAbilities()) {
            if (sa.getPayCosts() != null && sa.getPayCosts().toString().contains("iscard")) {
                return sa;
            }
        }
        throw new AssertionError("no discard cost on " + c);
    }

    @Test(timeOut = 120000)
    public void testCyclingLandInGraveyardDoesNotHoldTheLandDrop() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        for (int i = 0; i < 7; i++) {
            addCard("Forest", ai);
        }
        addCardToZone("Sheltering Landscape", ai, ZoneType.Graveyard);
        Card land = addCardToZone("Forest", ai, ZoneType.Hand);
        addCardToZone("Rain of Blades", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        playUntilNextTurn(game);
        assertEquals(land.getZone().getZoneType(), ZoneType.Battlefield);
    }

    @Test(timeOut = 120000)
    public void testUsableFromItsZone() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Card cycled = addCardToZone("Sheltering Landscape", ai, ZoneType.Graveyard);
        assertFalse(DiscardOutletAi.usableFromItsZone(cycled, withDiscard(cycled)));
        Card mongrel = addCard("Wild Mongrel", ai);
        assertTrue(DiscardOutletAi.usableFromItsZone(mongrel, withDiscard(mongrel)));
        Card buried = addCardToZone("Wild Mongrel", ai, ZoneType.Graveyard);
        assertFalse(DiscardOutletAi.usableFromItsZone(buried, withDiscard(buried)));
    }
}
