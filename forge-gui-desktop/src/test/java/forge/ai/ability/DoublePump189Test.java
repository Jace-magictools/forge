package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

// magic-tools: rule 189, NumAtt$ Double was read as +0 by the AI: Bulk Up never cast in 16 Commander games
public class DoublePump189Test extends AITest {

    private static boolean inGraveyard(Player p, String name) {
        return p.getCardsIn(ZoneType.Graveyard).anyMatch(c -> c.getName().equals(name));
    }

    private Game play(String aiCreature, int oppLife) {
        Game game = initAndCreateGame();
        Player opp = game.getPlayers().get(0);
        Player ai = game.getPlayers().get(1);
        addCard("Mountain", ai);
        addCard("Mountain", ai);
        addCard(aiCreature, ai).setSickness(false);
        addCardToZone("Bulk Up", ai, ZoneType.Hand);
        opp.setLife(oppLife, null);
        fillLibrary(opp, 20);
        fillLibrary(ai, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        return game;
    }

    private void toEndOfCombat(Game game) {
        playUntilPhase(game, PhaseType.MAIN2);
    }

    @Test
    public void testDoubleForLethal() {
        // Hill Giant 3/3 alone, opponent at 5: 3 is not enough, doubled to 6 it is
        Game game = play("Hill Giant", 5);
        toEndOfCombat(game);
        // getPlayers() drops the loser: the registered list keeps them
        assertTrue(inGraveyard(game.getRegisteredPlayers().get(1), "Bulk Up"), "Bulk Up should be cast");
        assertTrue(game.getRegisteredPlayers().get(0).getLife() <= 0, "doubled giant is lethal");
    }

    @Test
    public void testKeptWithoutPower() {
        // a 0/3 wall doubles to nothing
        Game game = play("Wall of Wood", 5);
        toEndOfCombat(game);
        assertFalse(inGraveyard(game.getPlayers().get(1), "Bulk Up"), "nothing to double");
        assertEquals(game.getPlayers().get(0).getLife(), 5);
    }
}
