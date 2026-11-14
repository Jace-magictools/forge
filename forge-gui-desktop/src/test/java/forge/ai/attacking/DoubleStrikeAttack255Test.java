package forge.ai.attacking;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;

import static org.testng.Assert.assertTrue;

// magic-tools rule 255 (D145, tester 08/11, a test game): Blade Historian gives double strike to
// attacking creatures; the AI did not see it before declaring and kept Alpine Watchdog home facing a lone Archon of
// Sun's Grace 3/4 (blocked: 2 first strike + 2 kills the Archon, an even trade at worst). Control: without the
// Historian (Glory Seeker instead), a 2/2 bounces off a 3/4 and stays home.
public class DoubleStrikeAttack255Test extends AITest {

    private Player[] game(String helper) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard("Archon of Sun's Grace", opponent).setSickness(false);
        Card dog = addCard("Alpine Watchdog", ai);
        dog.setSickness(false);
        Card h = addCard(helper, ai);
        h.setSickness(true);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        ai.addLandPlayedThisTurn();
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        assertTrue(forge.ai.AttackStrike255.hasDoubleStrike(dog) == helper.equals("Blade Historian"), "prediction");
        playUntilPhase(game, PhaseType.MAIN2);
        return new Player[] {ai, opponent};
    }

    private static boolean attacked(Player ai, Player opp) {
        return opp.getLife() < 20 || opp.getCreaturesInPlay().isEmpty() || ai.getCreaturesInPlay().size() < 2;
    }

    @Test(timeOut = 120000)
    public void testAttacksWithHistorian() {
        Player[] p = game("Blade Historian");
        assertTrue(attacked(p[0], p[1]), "Watchdog attacked with double strike, opp life " + p[1].getLife());
    }

    @Test(timeOut = 120000)
    public void testStaysHomeWithoutHistorian() {
        Player[] p = game("Glory Seeker");
        assertTrue(!attacked(p[0], p[1]), "Watchdog stayed home, opp life " + p[1].getLife());
    }
}
