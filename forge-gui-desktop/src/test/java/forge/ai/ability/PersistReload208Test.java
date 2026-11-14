package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.CounterEnumType;
import forge.game.phase.PhaseType;
import forge.game.player.Player;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

// magic-tools rules 208-209 (D124, D125, tester 05/11, Pod a test game). 208: a +1/+1 counter goes on
// the persist creature that has its -1/-1 counter, the two cancel. 209: a permanent that puts that counter on the creature
// each time it comes back (Thran Vigil, Cathars' Crusade) makes the persist loop, like Melira.
public class PersistReload208Test extends AITest {

    private Player loopGame(String reloader) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        if (reloader != null) {
            addCard(reloader, ai);
        }
        addCard("Murderous Redcap", ai);
        addCard("Viscera Seer", ai);
        for (int i = 0; i < 4; i++) {
            addCard("Wall of Stone", opponent);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilNextTurn(game);
        return opponent;
    }

    @Test(timeOut = 120000)
    public void testThranVigilLoops() {
        Player opponent = loopGame("Thran Vigil");
        assertTrue(opponent.getLife() <= 0, "life " + opponent.getLife());
    }

    @Test(timeOut = 120000)
    public void testCatharsCrusadeLoops() {
        Player opponent = loopGame("Cathars' Crusade");
        assertTrue(opponent.getLife() <= 0, "life " + opponent.getLife());
    }

    // nothing puts the counter back: the Redcap stays where it is, no endless loop
    @Test(timeOut = 120000)
    public void testWithoutReloaderNoLoop() {
        Player opponent = loopGame(null);
        assertTrue(opponent.getLife() >= 16, "life " + opponent.getLife());
    }

    // Rule 208: Thran Vigil-like target choice, the persist creature with the -1/-1 counter over a stronger one
    @Test(timeOut = 120000)
    public void testCounterGoesOnPersistWithMinusCounter() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Card finks = addCard("Kitchen Finks", ai);
        Card elite = addCard("Safehold Elite", ai);
        Card ogre = addCard("Hill Giant", ai);
        finks.addCounterInternal(CounterEnumType.M1M1, 1, ai, false, null, null);
        game.getAction().checkStateEffects(true);
        forge.game.card.CardCollection all = new forge.game.card.CardCollection();
        all.add(ogre);
        all.add(elite);
        all.add(finks);
        assertEquals(CountersAi.chooseBoonTarget(all, "P1P1"), finks);
    }
}
