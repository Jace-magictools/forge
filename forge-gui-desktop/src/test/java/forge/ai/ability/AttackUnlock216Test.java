package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools rule 216 (tester 02/11, a test game): the permanent that lifts an attack limit comes
// down before combat; a spell that lifts nothing still waits for main 2, and so does one that would lose a bonus.
public class AttackUnlock216Test extends AITest {

    private Game setup(String attacker, String inHand, String... extra) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Card c = addCard(attacker, ai);
        c.setSickness(false);
        for (int i = 0; i < 6; i++) {
            addCard("Forest", ai);
        }
        for (String e : extra) {
            addCard(e, ai);
        }
        addCardToZone(inHand, ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(game.getPlayers().get(0), 20);
        ai.setLandsPlayedThisTurn(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        return game;
    }

    private int castBeforeCombat(Game game, String name) {
        playUntilPhase(game, PhaseType.COMBAT_BEGIN);
        return countCardsWithName(game, name, ZoneType.Battlefield);
    }

    @Test(timeOut = 120000)
    public void testSecondDinosaurBeforeTheStun() {
        Game game = setup("Pugnacious Hammerskull", "Pugnacious Hammerskull");
        assertEquals(castBeforeCombat(game, "Pugnacious Hammerskull"), 2, gameStateToString(game));
    }

    @Test(timeOut = 120000)
    public void testOtherDinosaurLifts() {
        Game game = setup("Pugnacious Hammerskull", "Colossal Dreadmaw");
        assertEquals(castBeforeCombat(game, "Colossal Dreadmaw"), 1, gameStateToString(game));
    }

    @Test(timeOut = 120000)
    public void testNonDinosaurWaits() {
        Game game = setup("Pugnacious Hammerskull", "Grizzly Bears");
        assertEquals(castBeforeCombat(game, "Grizzly Bears"), 0, gameStateToString(game));
    }

    @Test(timeOut = 120000)
    public void testAlreadyLiftedWaits() {
        Game game = setup("Pugnacious Hammerskull", "Pugnacious Hammerskull", "Colossal Dreadmaw");
        assertEquals(castBeforeCombat(game, "Pugnacious Hammerskull"), 1, gameStateToString(game));
    }

    @Test(timeOut = 120000)
    public void testCantAttackUnlessLifted() {
        Game game = setup("Warden of the Chained", "Colossal Dreadmaw");
        assertEquals(castBeforeCombat(game, "Colossal Dreadmaw"), 1, gameStateToString(game));
    }
}
