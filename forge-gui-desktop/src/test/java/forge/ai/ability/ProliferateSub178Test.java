package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertTrue;

// magic-tools: rule 178, a Proliferate sub-ability with nothing to proliferate does not veto the spell
// (Infection: Drown in Ichor held 5 turns facing a 3/1 and a 2/3 it kills)
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
        gameLoopUntilNextPhase(game);
        assertTrue(ai.getCardsIn(ZoneType.Graveyard).anyMatch(c -> c.getName().equals("Drown in Ichor")),
                "hand=" + ai.getCardsIn(ZoneType.Hand) + " oppbf=" + opp.getCardsIn(ZoneType.Battlefield));
        assertTrue(opp.getCardsIn(ZoneType.Graveyard).anyMatch(c -> c.getName().equals("Veteran Motorist")));
    }
}
