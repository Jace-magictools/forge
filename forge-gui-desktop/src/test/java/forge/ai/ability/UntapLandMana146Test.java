package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertTrue;

// magic-tools rule 146: Krosan Restorer ({T}: untap target land; threshold: up to three) is mana. With threshold,
// Lotus Field (3) + two Mountains are untapped every main phase: 5 mana pay Aggravated Assault ({3}{R}{R}), which
// untaps the Restorer: endless combats (combo relue « vraie », puzzle MT20). Without threshold, Lotus Field is tapped,
// untapped and tapped again: 6 mana, one Assault more.
public class UntapLandMana146Test extends AITest {

    private Player assaultGame(boolean restorer, boolean threshold) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard("Lotus Field", ai);
        if (threshold) {
            addCard("Mountain", ai);
            addCard("Mountain", ai);
            for (int i = 0; i < 7; i++) {
                addCardToZone("Mountain", ai, ZoneType.Graveyard);
            }
        }
        addCard("Aggravated Assault", ai);
        if (restorer) {
            addCard("Krosan Restorer", ai).setSickness(false);
        }
        addCard("Craw Wurm", ai).setSickness(false);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        ai.addLandPlayedThisTurn();
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilNextTurn(game);
        return opponent;
    }

    @Test(timeOut = 120000)
    public void testThresholdRestorerLoopsAssault() {
        Player opponent = assaultGame(true, true);
        assertTrue(opponent.getLife() <= 0, "life " + opponent.getLife());
    }

    // without the Restorer, the five lands pay one Assault: two combats
    @Test(timeOut = 120000)
    public void testWithoutRestorerTwoCombats() {
        Player opponent = assaultGame(false, true);
        assertTrue(opponent.getLife() >= 8, "life " + opponent.getLife());
    }

    // no threshold: Lotus Field tapped, untapped, tapped again (6 mana) pays one Assault the lone Lotus never paid
    @Test(timeOut = 120000)
    public void testLoneLotusFloatsForAssault() {
        Player opponent = assaultGame(true, false);
        assertTrue(opponent.getLife() <= 8, "life " + opponent.getLife());
    }
}
