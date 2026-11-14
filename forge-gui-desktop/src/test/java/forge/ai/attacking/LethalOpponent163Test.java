package forge.ai.attacking;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.ai.AiAttackController;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

// magic-tools rule 163 (table à 3-4 du site, 04/11): the defender came from a threat score and the kill check only
// looked at that player. Four Grizzly Bears, one opponent with five Craw Wurms (the threat), the other at 6 life with
// a single Grizzly Bears: three unblocked Bears kill the weak one. Control: at 9 life nobody can be killed.
public class LethalOpponent163Test extends AITest {

    private Player[] game(int weakLife) {
        Game game = initAndCreateThreePlayerGame();
        Player threat = game.getPlayers().get(0);
        Player ai = game.getPlayers().get(1);
        Player weak = game.getPlayers().get(2);
        threat.setLife(20, null);
        weak.setLife(weakLife, null);
        for (int i = 0; i < 5; i++) {
            addCard("Craw Wurm", threat);
        }
        addCard("Grizzly Bears", weak);
        for (int i = 0; i < 4; i++) {
            Card bear = addCard("Grizzly Bears", ai);
            bear.setSickness(false);
        }
        for (Player p : game.getPlayers()) {
            fillLibrary(p, 20);
        }
        ai.addLandPlayedThisTurn();
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        return new Player[] {threat, ai, weak};
    }

    @Test(timeOut = 120000)
    public void testThreatScorePicksTheOtherOpponent() {
        Player[] p = game(6);
        for (int i = 0; i < 20; i++) {
            assertEquals(AiAttackController.choosePreferredDefenderPlayer(p[1], true), p[0],
                    "the board threat is the preferred defender, so the kill needs rule 163");
        }
    }

    @Test(timeOut = 120000)
    public void testKillsTheOpponentWithinReach() {
        Player[] p = game(6);
        playUntilPhase(p[1].getGame(), PhaseType.MAIN2);
        assertTrue(p[2].getLife() <= 0 || p[2].hasLost(), "the opponent at 6 died, life " + p[2].getLife());
    }

    @Test(timeOut = 120000)
    public void testNoKillNoChange() {
        Player[] p = game(9);
        playUntilPhase(p[1].getGame(), PhaseType.MAIN2);
        assertTrue(p[2].getLife() > 0, "nobody can be killed at 9 life");
    }
}
