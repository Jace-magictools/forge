package forge.ai.attacking;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.CounterEnumType;
import forge.game.phase.PhaseType;
import forge.game.player.Player;

import static org.testng.Assert.assertTrue;

// magic-tools rule 155: Dreadhorde Butcher (3/3 with two counters) against a 3/1 flyer, the position of the Plateau
// (a test game, 02/11). Blocked, both die and the Butcher still deals 3 on its death; unblocked, 3 to the face.
// A vanilla 3/3 in the same spot is a plain trade and keeps Forge's usual caution.
public class DeathPays155Test extends AITest {

    private Player[] game(String name, int counters) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        ai.setLife(18, null);
        opponent.setLife(17, null);
        addCard("Dreamshackle Geist", opponent);
        Card raptor = addCard("Cloudfin Raptor", opponent);
        raptor.setCounters(CounterEnumType.P1P1, 1);
        raptor.setTapped(true);
        addCard("Favorable Winds", opponent);
        Card c = addCard(name, ai);
        c.setSickness(false);
        if (counters > 0) {
            c.setCounters(CounterEnumType.P1P1, counters);
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
    public void testButcherAttacks() {
        Player[] p = game("Dreadhorde Butcher", 2);
        boolean attacked = p[1].getLife() < 17 || p[1].getCreaturesInPlay().size() < 2
                || p[0].getCreaturesInPlay().isEmpty() || p[0].getCreaturesInPlay().get(0).getCounters(CounterEnumType.P1P1) > 2;
        assertTrue(attacked, "Butcher attacked, opp life " + p[1].getLife());
    }

    // control: a vanilla 3/3 trading with the flyer, nothing from its death
    @Test(timeOut = 120000)
    public void testVanillaStaysHome() {
        Player[] p = game("Centaur Courser", 0);
        assertTrue(p[1].getLife() == 17 && p[1].getCreaturesInPlay().size() == 2, "vanilla stayed home, opp life " + p[1].getLife());
    }
}
