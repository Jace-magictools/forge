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

// magic-tools rule 313 (D177, plateau 13/11, tester): Teleportation Circle ("at the beginning of your end step,
// exile up to one target artifact or creature you control, then return it"). An optional blink of our own permanents
// takes the one whose return is worth the most: an arrival trigger with something to hit, or shedding -1/-1 counters
// or an opposing aura; nothing when no return is worth it (an arrival whose required target does not exist is worth 0).
public class OptionalBlink313Test extends AITest {

    private Game setup(boolean mage, int mageCounters, boolean oppCreature, int bearsM1M1) {
        Game game = initAndCreateGame();
        Player opp = game.getPlayers().get(0);
        Player ai = game.getPlayers().get(1);
        addCard("Teleportation Circle", ai);
        if (mage) {
            Card m = addCard("Reflector Mage", ai);
            if (mageCounters > 0) {
                m.addCounterInternal(CounterEnumType.P1P1, mageCounters, ai, false, null, null);
            }
        }
        Card bears = addCard("Grizzly Bears", ai);
        if (bearsM1M1 > 0) {
            bears.addCounterInternal(CounterEnumType.M1M1, bearsM1M1, ai, false, null, null);
        } else {
            bears.addCounterInternal(CounterEnumType.P1P1, 1, ai, false, null, null);
        }
        if (oppCreature) {
            addCard("Serra Angel", opp);
        }
        for (Player p : game.getPlayers()) {
            fillLibrary(p, 20);
        }
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        return game;
    }

    @Test(timeOut = 120000)
    public void testBlinksReflectorMage() {
        Game game = setup(true, 0, true, 0);
        playUntilPhase(game, PhaseType.CLEANUP);
        assertEquals(countCardsWithName(game, "Serra Angel", ZoneType.Hand), 1, "Reflector Mage not blinked");
    }

    @Test(timeOut = 120000)
    public void testNoTargetForTheArrival() {
        Game game = setup(true, 1, false, 0);
        playUntilPhase(game, PhaseType.CLEANUP);
        Card mage = findCardWithName(game, "Reflector Mage");
        assertEquals(mage.getCounters(CounterEnumType.P1P1), 1, "Reflector Mage blinked with nothing to bounce, lost its counter");
    }

    @Test(timeOut = 120000)
    public void testShedsMinusCounters() {
        Game game = setup(false, 0, true, 1);
        playUntilPhase(game, PhaseType.CLEANUP);
        Card bears = findCardWithName(game, "Grizzly Bears");
        assertEquals(bears.getCounters(CounterEnumType.M1M1), 0, "Grizzly Bears kept its -1/-1 counter");
    }

    @Test(timeOut = 120000)
    public void testNothingWorthBlinking() {
        Game game = setup(false, 0, true, 0);
        playUntilPhase(game, PhaseType.CLEANUP);
        Card bears = findCardWithName(game, "Grizzly Bears");
        assertEquals(bears.getCounters(CounterEnumType.P1P1), 1, "Grizzly Bears blinked for nothing, lost its counter");
    }
}
