package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import forge.ai.SweeperUnderPressure249;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

// magic-tools: rule 249, D141 (tester 08/11, a test game T6): 7 life against a 6/6 and two
// Realmwalkers, the AI cast Darksteel Ingot and Expedition Map instead of Nevinyrral's Disk.
public class SweeperUnderPressure249Test extends AITest {

    @Test
    public void testDiskBeforeIngot() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        addCard("Island", ai);
        addCard("Island", ai);
        addCard("Urza's Power Plant", ai);
        addCard("Urza's Power Plant", ai);
        addCard("Trophy Mage", ai);
        for (String c : new String[] {"Nevinyrral's Disk", "Ulamog, the Infinite Gyre", "Trinket Mage",
                "Expedition Map", "Endbringer", "Darksteel Ingot"}) {
            addCardToZone(c, ai, ZoneType.Hand);
        }
        addCard("Forest", opponent);
        addCard("Mountain", opponent);
        addCard("Mountain", opponent);
        addCard("Realmwalker", opponent);
        addCard("Realmwalker", opponent);
        addCard("Pugnacious Hammerskull", opponent);
        ai.setLife(7, null);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        ai.setLandsPlayedThisTurn(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Nevinyrral's Disk", ZoneType.Battlefield), 1, gameStateToString(game));
    }

    /** no pressure (one 2/3 in front, 20 life): nothing is held for the Disk */
    @Test
    public void testNoPressureNothingHeld() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        for (String l : new String[] {"Island", "Island", "Urza's Power Plant", "Urza's Power Plant"}) {
            addCard(l, ai);
        }
        addCardToZone("Nevinyrral's Disk", ai, ZoneType.Hand);
        forge.game.card.Card ingot = addCardToZone("Darksteel Ingot", ai, ZoneType.Hand);
        addCard("Realmwalker", opponent);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        forge.game.spellability.SpellAbility sa = ingot.getFirstSpellAbility();
        sa.setActivatingPlayer(ai);
        assertFalse(SweeperUnderPressure249.underPressure(ai));
        assertNull(SweeperUnderPressure249.sweeperKept(ai, sa));
        addCard("Pugnacious Hammerskull", opponent);
        ai.setLife(7, null);
        assertTrue(SweeperUnderPressure249.underPressure(ai));
        assertNotNull(SweeperUnderPressure249.sweeperKept(ai, sa));
    }

    /** Pyroclasm (2 damage) against a 6/6 is no relief: nothing is held for it */
    @Test
    public void testPyroclasmNoRelief() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        for (String l : new String[] {"Mountain", "Mountain", "Mountain"}) {
            addCard(l, ai);
        }
        addCardToZone("Pyroclasm", ai, ZoneType.Hand);
        forge.game.card.Card bear = addCardToZone("Grizzly Bears", ai, ZoneType.Hand);
        addCard("Pugnacious Hammerskull", opponent);
        ai.setLife(7, null);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        forge.game.spellability.SpellAbility sa = bear.getFirstSpellAbility();
        sa.setActivatingPlayer(ai);
        assertTrue(SweeperUnderPressure249.underPressure(ai));
        assertNull(SweeperUnderPressure249.sweeperKept(ai, sa));
    }
}
