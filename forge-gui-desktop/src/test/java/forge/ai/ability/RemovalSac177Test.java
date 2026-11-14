package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

// magic-tools: rule 177, a creature sacrificed to remove an opponent's permanent worth clearly more
// (Vampire: Dusk Rose Reliquary held 5 turns with five creatures in play, Aurelia across the table)
public class RemovalSac177Test extends AITest {

    private Player setup(Game game, String threat) {
        Player opp = game.getPlayers().get(0);
        Player ai = game.getPlayers().get(1);
        for (int i = 0; i < 3; i++) {
            addCard("Plains", ai);
        }
        addCardToZone("Dusk Rose Reliquary", ai, ZoneType.Hand);
        addCard("Essence Channeler", ai);
        addCard(threat, opp);
        addCard("Mountain", opp);
        fillLibrary(opp, 20);
        fillLibrary(ai, 20);
        moveToMain2(game, ai);
        gameLoopUntilNextPhase(game);
        return ai;
    }

    @Test
    public void testReliquaryCastOnBigThreat() {
        Game game = initAndCreateGame();
        Player ai = setup(game, "Aurelia, the Law Above");
        assertTrue(ai.getCardsIn(ZoneType.Battlefield).anyMatch(c -> c.getName().equals("Dusk Rose Reliquary")),
                "bf=" + ai.getCardsIn(ZoneType.Battlefield));
    }

    @Test
    public void testReliquaryKeptOnSmallThreat() {
        Game game = initAndCreateGame();
        Player ai = setup(game, "Grizzly Bears");
        assertFalse(ai.getCardsIn(ZoneType.Battlefield).anyMatch(c -> c.getName().equals("Dusk Rose Reliquary")),
                "bf=" + ai.getCardsIn(ZoneType.Battlefield));
    }
}
