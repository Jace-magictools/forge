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

// magic-tools: rule 192, spells marked AI:RemoveDeck:All with one permanent sacrificed on top of the mana stayed in hand
public class SacrificeSpell192Test extends AITest {

    private static boolean has(Player p, ZoneType zone, String name) {
        return p.getCardsIn(zone).anyMatch(c -> c.getName().equals(name));
    }

    private Game play(String spell, String land, String[] ours, String... theirs) {
        Game game = initAndCreateGame();
        Player opp = game.getPlayers().get(0);
        Player ai = game.getPlayers().get(1);
        for (int i = 0; i < 5; i++) {
            addCard(land, ai);
        }
        addCardToZone(spell, ai, ZoneType.Hand);
        for (String c : ours) {
            addCard(c, ai).setSickness(false);
        }
        for (String c : theirs) {
            addCard(c, opp).setSickness(false);
        }
        fillLibrary(opp, 20);
        fillLibrary(ai, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        return game;
    }

    @Test
    public void testRemovalPaidWithOurWorstCreature() {
        Game game = play("Death Bomb", "Swamp", new String[] {"Llanowar Elves", "Hill Giant"}, "Serra Angel");
        Player ai = game.getRegisteredPlayers().get(1);
        assertFalse(has(ai, ZoneType.Hand, "Death Bomb"), "cast");
        assertTrue(has(ai, ZoneType.Graveyard, "Llanowar Elves"), "the elves pay");
        assertTrue(has(ai, ZoneType.Battlefield, "Hill Giant"), "the giant stays");
        assertEquals(game.getRegisteredPlayers().get(0).getCreaturesInPlay().size(), 0, "the angel dies");
    }

    @Test
    public void testRemovalNotForASmallerCreature() {
        Game game = play("Death Bomb", "Swamp", new String[] {"Serra Angel"}, "Llanowar Elves");
        Player ai = game.getRegisteredPlayers().get(1);
        assertTrue(has(ai, ZoneType.Hand, "Death Bomb"), "kept");
        assertTrue(has(ai, ZoneType.Battlefield, "Serra Angel"), "the angel stays");
    }

    @Test
    public void testNothingToSacrificeStaysInHand() {
        Game game = play("Improvised Club", "Mountain", new String[0], "Serra Angel");
        assertTrue(has(game.getRegisteredPlayers().get(1), ZoneType.Hand, "Improvised Club"), "kept");
    }

    @Test
    public void testRealCreatureNotBurnedForCards() {
        Game game = play("Vivisection", "Island", new String[] {"Serra Angel"}, "Hill Giant");
        Player ai = game.getRegisteredPlayers().get(1);
        assertTrue(has(ai, ZoneType.Hand, "Vivisection"), "kept");
        assertTrue(has(ai, ZoneType.Battlefield, "Serra Angel"), "the angel stays");
    }

    @Test
    public void testFreeFodderPaysForCards() {
        Game game = play("Vivisection", "Island", new String[] {"Bloodghast"}, "Hill Giant");
        Player ai = game.getRegisteredPlayers().get(1);
        assertFalse(has(ai, ZoneType.Hand, "Vivisection"), "cast");
        assertTrue(has(ai, ZoneType.Graveyard, "Bloodghast"), "the recursive creature pays");
    }

    @Test
    public void testFreeFodderPaysForDamage() {
        Game game = play("Improvised Club", "Mountain", new String[] {"Bloodghast"}, "Serra Angel");
        // burn waits for the end of the opponent's turn: play through it, Lightning Bolt behaves the same
        playUntilNextTurn(game);
        playUntilNextTurn(game);
        assertFalse(has(game.getRegisteredPlayers().get(1), ZoneType.Hand, "Improvised Club"), "cast");
        Player opp = game.getRegisteredPlayers().get(0);
        assertTrue(opp.getCreaturesInPlay().isEmpty() || opp.getLife() == 16, "the 4 damage landed");
    }

    @Test
    public void testAllSacrificesStayOut() {
        Game game = play("Kaervek's Spite", "Swamp", new String[] {"Hill Giant"}, "Serra Angel");
        Player ai = game.getRegisteredPlayers().get(1);
        assertTrue(has(ai, ZoneType.Battlefield, "Hill Giant"), "no wipe of our own board");
    }
}
