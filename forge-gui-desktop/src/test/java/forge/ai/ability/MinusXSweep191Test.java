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

// magic-tools: rule 191, -X/-X with a chosen X was read as -0/-0 when deciding: Toxic Deluge was never cast
public class MinusXSweep191Test extends AITest {

    private static boolean has(Player p, ZoneType zone, String name) {
        return p.getCardsIn(zone).anyMatch(c -> c.getName().equals(name));
    }

    private Game play(int aiLife, String[] ours, String... theirs) {
        Game game = initAndCreateGame();
        Player opp = game.getPlayers().get(0);
        Player ai = game.getPlayers().get(1);
        for (int i = 0; i < 4; i++) {
            addCard("Swamp", ai);
        }
        addCardToZone("Toxic Deluge", ai, ZoneType.Hand);
        for (String c : ours) {
            addCard(c, ai).setSickness(false);
        }
        for (String c : theirs) {
            addCard(c, opp).setSickness(false);
        }
        ai.setLife(aiLife, null);
        fillLibrary(opp, 20);
        fillLibrary(ai, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        return game;
    }

    @Test
    public void testSweepsAtTheNeededX() {
        Game game = play(20, new String[0], "Serra Angel", "Serra Angel", "Hill Giant");
        Player ai = game.getRegisteredPlayers().get(1);
        assertFalse(has(ai, ZoneType.Hand, "Toxic Deluge"), "cast");
        assertEquals(ai.getLife(), 16, "pays 4");
        assertEquals(game.getRegisteredPlayers().get(0).getCreaturesInPlay().size(), 0);
    }

    @Test
    public void testSparesOurBiggerCreature() {
        Game game = play(20, new String[] {"Craw Wurm"}, "Hill Giant", "Hill Giant");
        Player ai = game.getRegisteredPlayers().get(1);
        assertEquals(game.getRegisteredPlayers().get(0).getCreaturesInPlay().size(), 0, "their 3/3s die");
        assertTrue(has(ai, ZoneType.Battlefield, "Craw Wurm"), "our 6/4 survives");
    }

    @Test
    public void testNotForASmallCreature() {
        Game game = play(20, new String[0], "Llanowar Elves");
        assertTrue(has(game.getRegisteredPlayers().get(1), ZoneType.Hand, "Toxic Deluge"), "kept");
    }

    @Test
    public void testKeepsSomeLife() {
        Game game = play(6, new String[0], "Serra Angel", "Serra Angel");
        Player ai = game.getRegisteredPlayers().get(1);
        assertEquals(ai.getLife(), 6, "X = 4 would leave 2 life");
        assertTrue(has(ai, ZoneType.Hand, "Toxic Deluge"), "kept");
    }
}
