package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.player.Player;
import forge.game.zone.ZoneType;
import forge.util.MyRandom;

import java.security.SecureRandom;
import java.util.Random;

import static org.testng.Assert.assertTrue;

// magic-tools: rule 178, a Proliferate sub-ability with nothing to proliferate does not veto the spell
// (Infection: Drown in Ichor held 5 turns facing a 3/1 and a 2/3 it kills). Seen flaky without the forced roll.
public class ProliferateSub178Test extends AITest {

    @Test
    public void testDrownInIchorCastWithoutCounters() {
        Game game = initAndCreateGame();
        Player opp = game.getPlayers().get(0);
        Player ai = game.getPlayers().get(1);
        addCard("Swamp", ai);
        addCard("Swamp", ai);
        addCardToZone("Drown in Ichor", ai, ZoneType.Hand);
        addCard("Veteran Motorist", opp);
        addCard("Mountain", opp);
        fillLibrary(opp, 20);
        fillLibrary(ai, 20);
        moveToMain2(game, ai);
        // Forge itself rolls whether to use removal now (ComputerUtilCard.useRemovalNow, ~70 % here): not what rule 178
        // is about, so the roll is forced to "now" (draws 0), then the random source is put back.
        MyRandom.setRandom(new Random() {
            @Override protected int next(int bits) { return 0; }
        });
        try {
            gameLoopUntilNextPhase(game);
        } finally {
            MyRandom.setRandom(new SecureRandom());
        }
        assertTrue(ai.getCardsIn(ZoneType.Graveyard).anyMatch(c -> c.getName().equals("Drown in Ichor")),
                "hand=" + ai.getCardsIn(ZoneType.Hand) + " oppbf=" + opp.getCardsIn(ZoneType.Battlefield));
        assertTrue(opp.getCardsIn(ZoneType.Graveyard).anyMatch(c -> c.getName().equals("Veteran Motorist")));
    }
}
