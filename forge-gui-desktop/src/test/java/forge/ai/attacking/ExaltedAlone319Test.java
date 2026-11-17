package forge.ai.attacking;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

// magic-tools rule 319 (D204, plateau 16/11, a tester): exalted makes a lone attacker bigger. The AI did not
// attack with Akrasan Squire 1/1 (3/3 alone with two exalted) into an Augur of Bolas 1/3, and with Rafiq of the
// Many on the battlefield it attacked with two creatures (no exalted at all) instead of one.
public class ExaltedAlone319Test extends AITest {

    private Player[] game(boolean rafiq) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard("Augur of Bolas", opponent).setSickness(false);
        addCard("Akrasan Squire", ai).setSickness(false);
        addCard("Qasali Pridemage", ai).setSickness(!rafiq);
        if (rafiq) {
            addCard("Rafiq of the Many", ai);
            addCard("Sigiled Paladin", ai).setSickness(false);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        ai.addLandPlayedThisTurn();
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.MAIN2);
        return new Player[] {ai, opponent};
    }

    // the human game, turn 10: the opponent has no creature, Rafiq cast this turn, five exalted in all
    private Player[] emptyBoard() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard("Island", opponent);
        addCard("Akrasan Squire", ai).setSickness(false);
        addCard("Qasali Pridemage", ai).setSickness(false);
        addCard("Giltspire Avenger", ai).setSickness(false);
        addCard("Cathedral of War", ai);
        addCard("Rafiq of the Many", ai);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        ai.addLandPlayedThisTurn();
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.MAIN2);
        return new Player[] {ai, opponent};
    }

    @Test(timeOut = 120000)
    public void testOneAttackerWithRafiqOnEmptyBoard() {
        Player[] p = emptyBoard();
        assertEquals(tapped(p[0]), 1L, "a single exalted attacker, opp life " + p[1].getLife());
        assertTrue(p[1].getLife() <= 6, "lone attacker with five exalted and double strike, opp life " + p[1].getLife());
    }

    private static long tapped(Player p) {
        return p.getCreaturesInPlay().stream().filter(Card::isTapped).count();
    }

    @Test(timeOut = 120000)
    public void testSquireAttacksAloneWithTwoExalted() {
        Player[] p = game(false);
        assertTrue(tapped(p[0]) == 1 || p[1].getLife() < 20 || p[1].getCreaturesInPlay().isEmpty(),
                "Squire attacked alone, opp life " + p[1].getLife());
    }

    @Test(timeOut = 120000)
    public void testOneAttackerWithRafiq() {
        Player[] p = game(true);
        assertEquals(tapped(p[0]), 1L, "a single attacker with Rafiq, opp life " + p[1].getLife());
    }
}
