package forge.ai.attacking;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;

import static org.testng.Assert.assertTrue;

// magic-tools rule 311 (D175, plateau 12/11, tester): the AI at 2 life with a big lifelink creature and a 3/4 kept
// everything home against four 1-power creatures and died. Attacking with the lifelink one gives back its power even
// if blocked. Control: same board without lifelink, the AI at 2 stays home (tester: staying home is the default).
public class LifelinkAttack311Test extends AITest {

    private Player[] game(String big) {
        Game game = initAndCreateGame();
        Player opp = game.getPlayers().get(0);
        Player ai = game.getPlayers().get(1);
        opp.setLife(27, null);
        ai.setLife(2, null);
        for (int i = 0; i < 4; i++) {
            addCard("Llanowar Elves", opp).setSickness(false);
        }
        Card c = addCard(big, ai);
        c.setSickness(false);
        addCard("Deathbringer Liege", ai).setSickness(false);
        for (Player p : game.getPlayers()) {
            fillLibrary(p, 20);
        }
        ai.addLandPlayedThisTurn();
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        return new Player[] {opp, ai};
    }

    @Test(timeOut = 120000)
    public void testLifelinkAttackerAttacks() {
        Player[] p = game("Wurmcoil Engine");
        playUntilPhase(p[1].getGame(), PhaseType.MAIN2);
        assertTrue(p[1].getLife() >= 8, "Wurmcoil attacked and gained 6, life " + p[1].getLife());
    }

    @Test(timeOut = 120000)
    public void testNoLifelinkStaysHome() {
        Player[] p = game("Craw Wurm");
        playUntilPhase(p[1].getGame(), PhaseType.MAIN2);
        assertTrue(p[0].getLife() == 27, "no lifelink: the AI at 2 keeps its blockers, opp life " + p[0].getLife());
    }
}
