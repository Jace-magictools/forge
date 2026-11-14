package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.ai.DrawFullHand258;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

// magic-tools rule 258 (D147 point 4, tester 08/11, a test game decision 10): three Swamps, a hand of
// seven, Sign in Blood was cast instead of Vampire Nighthawk and a card was discarded at end of turn. The draw spell
// waits for the creature it would leave unpaid. Control: with a short hand the rule does not apply.
public class DrawFullHand258Test extends AITest {

    private Game setUp(boolean fullHand) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCards("Swamp", 3, ai);
        addCardToZone("Sign in Blood", ai, ZoneType.Hand);
        addCardToZone("Vampire Nighthawk", ai, ZoneType.Hand);
        if (fullHand) {
            addCardToZone("Vampire Nighthawk", ai, ZoneType.Hand);
            addCardToZone("Gatekeeper of Malakir", ai, ZoneType.Hand);
            addCardToZone("Drana, Kalastria Bloodchief", ai, ZoneType.Hand);
            addCardToZone("Swamp", ai, ZoneType.Hand);
            addCardToZone("Swamp", ai, ZoneType.Hand);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        ai.addLandPlayedThisTurn();
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        return game;
    }

    private static Card sib(Player ai) {
        for (Card c : ai.getCardsIn(ZoneType.Hand)) {
            if (c.getName().equals("Sign in Blood")) {
                c.getFirstSpellAbility().setActivatingPlayer(ai);
                return c;
            }
        }
        return null;
    }

    @Test(timeOut = 120000)
    public void testNighthawkBeforeSignInBlood() {
        Game game = setUp(true);
        Player ai = game.getPlayers().get(1);
        assertNotNull(DrawFullHand258.drawKept(ai, sib(ai).getFirstSpellAbility()), "rule applies");
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Vampire Nighthawk", ZoneType.Battlefield), 1, "Nighthawk cast");
        assertEquals(countCardsWithName(game, "Sign in Blood", ZoneType.Hand), 1, "Sign in Blood kept");
    }

    @Test(timeOut = 120000)
    public void testShortHandNotConcerned() {
        Game game = setUp(false);
        Player ai = game.getPlayers().get(1);
        assertNull(DrawFullHand258.drawKept(ai, sib(ai).getFirstSpellAbility()), "short hand");
    }
}
