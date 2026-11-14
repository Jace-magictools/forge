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

// magic-tools: rule 193, burn with X paid in life or from the graveyard stayed in hand (AI:RemoveDeck:All)
public class LifeOrGraveXBurn193Test extends AITest {

    private static boolean has(Player p, ZoneType zone, String name) {
        return p.getCardsIn(zone).anyMatch(c -> c.getName().equals(name));
    }

    private Game play(String spell, int life, int graveyard, String[] lands, String[] ours, String... theirs) {
        Game game = initAndCreateGame();
        Player opp = game.getPlayers().get(0);
        Player ai = game.getPlayers().get(1);
        ai.setLife(life, null);
        for (String l : lands) {
            addCard(l, ai);
        }
        addCardToZone(spell, ai, ZoneType.Hand);
        for (int i = 0; i < graveyard; i++) {
            addCardToZone("Hill Giant", ai, ZoneType.Graveyard);
        }
        for (String c : ours) {
            addCard(c, ai).setSickness(false);
        }
        for (String c : theirs) {
            addCard(c, opp).setSickness(false);
        }
        fillLibrary(opp, 20);
        fillLibrary(ai, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        // burn waits for the end of the opponent's turn: play through it
        playUntilPhase(game, PhaseType.END_OF_TURN);
        playUntilNextTurn(game);
        playUntilNextTurn(game);
        return game;
    }

    private static final String[] BR = {"Swamp", "Swamp", "Swamp", "Mountain", "Mountain", "Mountain"};
    private static final String[] R = {"Mountain", "Mountain", "Mountain", "Mountain"};

    @Test
    public void testCovenantBuysSeveralKillsWithExactLife() {
        Game game = play("Fire Covenant", 20, 0, BR, new String[0], "Serra Angel", "Hill Giant", "Llanowar Elves");
        Player ai = game.getRegisteredPlayers().get(1);
        assertFalse(has(ai, ZoneType.Hand, "Fire Covenant"), "cast");
        assertEquals(game.getRegisteredPlayers().get(0).getCreaturesInPlay().size(), 0, "all three die");
        assertEquals(ai.getLife(), 12, "4 + 3 + 1 life, not all of it");
    }

    @Test
    public void testCovenantKeepsTheLastLife() {
        Game game = play("Fire Covenant", 6, 0, BR, new String[0], "Serra Angel");
        Player ai = game.getRegisteredPlayers().get(1);
        assertTrue(has(ai, ZoneType.Hand, "Fire Covenant"), "4 for the angel would leave 2 life");
    }

    @Test
    public void testCovenantNotForALoneSmallCreature() {
        Game game = play("Fire Covenant", 20, 0, BR, new String[0], "Llanowar Elves");
        assertTrue(has(game.getRegisteredPlayers().get(1), ZoneType.Hand, "Fire Covenant"), "kept");
    }

    @Test
    public void testPyreExilesOnlyWhatKills() {
        Game game = play("Harvest Pyre", 20, 6, R, new String[0], "Serra Angel");
        Player ai = game.getRegisteredPlayers().get(1);
        assertFalse(has(ai, ZoneType.Hand, "Harvest Pyre"), "cast");
        assertEquals(game.getRegisteredPlayers().get(0).getCreaturesInPlay().size(), 0, "the angel dies");
        assertEquals(ai.getCardsIn(ZoneType.Exile).size(), 4, "4 cards exiled, not 6");
    }

    @Test
    public void testPyreShortOfCardsStaysInHand() {
        Game game = play("Harvest Pyre", 20, 2, R, new String[0], "Serra Angel");
        assertTrue(has(game.getRegisteredPlayers().get(1), ZoneType.Hand, "Harvest Pyre"), "2 cards cannot kill a 4/4");
    }
}
