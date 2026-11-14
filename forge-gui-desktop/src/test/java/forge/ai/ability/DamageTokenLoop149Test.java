package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.CounterEnumType;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

// magic-tools rule 149: Walking Ballista (5 counters) wears Druid's Call (dealt damage: that many Squirrels); Rosie Cotton
// of South Lane puts a +1/+1 counter on another creature of ours whenever we create a token. Shooting the Ballista costs
// nothing (the counter comes back) and makes a Squirrel, until the damage would kill it (combo relue « vraie », MT25).
public class DamageTokenLoop149Test extends AITest {

    private Player[] ballistaGame(boolean rosie) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        opponent.setLife(40, null);
        Card ballista = addCard("Walking Ballista", ai);
        ballista.setSickness(false);
        ballista.addCounterInternal(CounterEnumType.P1P1, 5, ai, false, null, null);
        Card call = addCard("Druid's Call", ai);
        call.attachToEntity(ballista, null);
        if (rosie) {
            addCard("Rosie Cotton of South Lane", ai).setSickness(false);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        ai.addLandPlayedThisTurn();
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.CLEANUP);
        return new Player[] {ai, opponent};
    }

    private static int squirrels(Player p) {
        int n = 0;
        for (Card c : p.getCardsIn(ZoneType.Battlefield)) {
            if (c.isToken() && c.getName().contains("Squirrel")) {
                n++;
            }
        }
        return n;
    }

    @Test(timeOut = 120000)
    public void testShootsItselfForSquirrels() {
        Player[] p = ballistaGame(true);
        assertEquals(squirrels(p[0]), 3, "squirrels");
        Card ballista = null;
        for (Card c : p[0].getCreaturesInPlay()) {
            if (c.getName().equals("Walking Ballista")) {
                ballista = c;
            }
        }
        assertTrue(ballista != null && ballista.getCounters(CounterEnumType.P1P1) == 5, "ballista kept its counters");
    }

    // without Rosie, a shot at itself loses a counter for a Squirrel: no loop
    @Test(timeOut = 120000)
    public void testWithoutRosieNoLoop() {
        Player[] p = ballistaGame(false);
        assertEquals(squirrels(p[0]), 0, "squirrels");
    }

    // rule 150: Darien, King of Kjeldor (we're dealt damage: that many Soldiers) + Rosie + Ballista: the Ballista shoots us,
    // a Soldier, the counter comes back; stops at 10 life + the opponents' power (20 - 10 - 2 = 8 Soldiers)
    private Player[] darienGame(boolean rosie) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        opponent.setLife(40, null);
        addCard("Grizzly Bears", opponent);
        Card ballista = addCard("Walking Ballista", ai);
        ballista.setSickness(false);
        ballista.addCounterInternal(CounterEnumType.P1P1, 3, ai, false, null, null);
        addCard("Darien, King of Kjeldor", ai).setSickness(false);
        if (rosie) {
            addCard("Rosie Cotton of South Lane", ai).setSickness(false);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        ai.addLandPlayedThisTurn();
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.CLEANUP);
        return new Player[] {ai, opponent};
    }

    private static int soldiers(Player p) {
        int n = 0;
        for (Card c : p.getCardsIn(ZoneType.Battlefield)) {
            if (c.isToken() && c.getName().contains("Soldier")) {
                n++;
            }
        }
        return n;
    }

    @Test(timeOut = 120000)
    public void testDarienShootsUsForSoldiers() {
        Player[] p = darienGame(true);
        assertEquals(soldiers(p[0]), 8, "soldiers");
        assertEquals(p[0].getLife(), 12, "life kept");
    }

    @Test(timeOut = 120000)
    public void testDarienWithoutRosieNoLoop() {
        Player[] p = darienGame(false);
        assertEquals(soldiers(p[0]), 0, "soldiers");
    }
}
