package forge.ai;

import org.testng.annotations.Test;

import forge.game.Game;
import forge.game.card.CounterEnumType;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertTrue;

// magic-tools rule 164 (table à 4 du site, 04/11, deck Energizer): Replicate—Pay {E}{E}{E} froze the game. Cost.add does
// not merge energy parts, each one was checked alone, so the AI kept adding copies up to Integer.MAX_VALUE.
// With 7 energy and three Grizzly Bears to kill, the AI casts Reiterating Bolt with 2 copies at most and the game goes on.
public class ReplicateEnergy164Test extends AITest {

    @Test(timeOut = 60000)
    public void testReplicateEnergyStopsAtTheEnergyPool() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        for (int i = 0; i < 3; i++) {
            addCard("Grizzly Bears", opponent);
        }
        addCard("Mountain", ai);
        addCard("Mountain", ai);
        addCardToZone("Reiterating Bolt", ai, ZoneType.Hand);
        ai.setCounters(CounterEnumType.ENERGY, 7);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        ai.addLandPlayedThisTurn();
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        int energy = ai.getCounters(CounterEnumType.ENERGY);
        assertTrue(energy >= 1 && energy <= 7, "energy left " + energy);
        assertTrue(energy % 3 == 1, "paid a whole number of replicate costs, energy left " + energy);
        int bears = opponent.getCreaturesInPlay().size();
        assertTrue(bears < 3, "the Bolt was cast, bears left " + bears);
    }
}
