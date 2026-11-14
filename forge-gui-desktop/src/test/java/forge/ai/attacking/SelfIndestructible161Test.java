package forge.ai.attacking;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertTrue;

// magic-tools rule 161: the banc position a test game (03/11). Adanto Vanguard (3/1 attacking, "Pay 4 life: gains
// indestructible") against a single untapped Grizzly Bears: it attacks, a block cannot kill it. Control: at 6 life the
// life cost is not safe, a block would kill it, it stays home.
public class SelfIndestructible161Test extends AITest {

    private Object[] game(int life) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        ai.setLife(life, null);
        opponent.setLife(20, null);
        Card bears = addCard("Grizzly Bears", opponent);
        Card adanto = addCard("Adanto Vanguard", ai);
        adanto.setSickness(false);
        for (int i = 0; i < 3; i++) {
            addCard("Plains", ai).setTapped(true);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        ai.addLandPlayedThisTurn();
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.MAIN2);
        return new Object[] {ai, opponent, adanto, bears};
    }

    @Test(timeOut = 120000)
    public void testAttacksWithPayableIndestructible() {
        Object[] p = game(16);
        Player opp = (Player) p[1];
        Card adanto = (Card) p[2];
        boolean attacked = opp.getLife() < 20 || !opp.getCreaturesInPlay().contains((Card) p[3]);
        assertTrue(attacked, "Adanto attacked, opp life " + opp.getLife());
        assertTrue(adanto.isInZone(ZoneType.Battlefield), "Adanto survived");
        // the life cost is paid once at most, and only when blocked
        assertTrue(((Player) p[0]).getLife() >= 12, "paid 4 life at most, life " + ((Player) p[0]).getLife());
    }

    @Test(timeOut = 120000)
    public void testStaysHomeWhenLifeTooLow() {
        Object[] p = game(6);
        Player opp = (Player) p[1];
        assertTrue(opp.getLife() == 20 && opp.getCreaturesInPlay().size() == 1, "Adanto stayed home, opp life " + opp.getLife());
    }
}
