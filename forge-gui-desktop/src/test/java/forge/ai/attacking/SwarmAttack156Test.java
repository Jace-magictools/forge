package forge.ai.attacking;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;

import static org.testng.Assert.assertTrue;

// magic-tools rule 156: the banc position a test game (03/11). Two 1/1 Soldier tokens against a flyer deck with a
// single untapped blocker (Warden of Evos Isle, 4/4 with two Favorable Winds) and no untapped land: the tokens cannot
// block flyers, so they attack and at least one connects. Control: a second untapped flyer, as many blockers as tokens.
public class SwarmAttack156Test extends AITest {

    private Player[] game(boolean secondBlockerUntapped) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        ai.setLife(12, null);
        opponent.setLife(13, null);
        addCard("Warden of Evos Isle", opponent);
        Card seer = addCard("Faerie Seer", opponent);
        seer.setTapped(!secondBlockerUntapped);
        addCard("Favorable Winds", opponent);
        addCard("Favorable Winds", opponent);
        for (int i = 0; i < 3; i++) {
            addCard("Island", opponent).setTapped(true);
        }
        for (int i = 0; i < 2; i++) {
            Card t = addToken("w_1_1_soldier", ai);
            t.setSickness(false);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        ai.addLandPlayedThisTurn();
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.MAIN2);
        return new Player[] {ai, opponent};
    }

    @Test(timeOut = 120000)
    public void testTokensSwarm() {
        Player[] p = game(false);
        boolean attacked = p[1].getLife() < 13 || p[0].getCreaturesInPlay().size() < 2;
        assertTrue(attacked, "tokens attacked, opp life " + p[1].getLife());
    }

    @Test(timeOut = 120000)
    public void testTokensStayHomeAgainstTwoBlockers() {
        Player[] p = game(true);
        assertTrue(p[1].getLife() == 13 && p[0].getCreaturesInPlay().size() == 2, "tokens stayed home, opp life " + p[1].getLife());
    }
}
