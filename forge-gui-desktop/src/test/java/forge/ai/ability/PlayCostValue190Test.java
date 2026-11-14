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

// magic-tools: rule 190, PlayCost$ PayLife<ConvertedManaCost> was read as 0: the AI could pay its whole life
public class PlayCostValue190Test extends AITest {

    private static boolean has(Player p, ZoneType zone, String name) {
        return p.getCardsIn(zone).anyMatch(c -> c.getName().equals(name));
    }

    private Game play(int aiLife, String... artifacts) {
        Game game = initAndCreateGame();
        Player opp = game.getPlayers().get(0);
        Player ai = game.getPlayers().get(1);
        addCard("Anrakyr the Traveller", ai).setSickness(false);
        for (String artifact : artifacts) {
            addCardToZone(artifact, ai, ZoneType.Hand);
        }
        ai.setLife(aiLife, null);
        fillLibrary(opp, 20);
        fillLibrary(ai, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        playUntilPhase(game, PhaseType.MAIN2);
        return game;
    }

    @Test
    public void testNoLethalPayment() {
        // Myr Battlesphere has mana value 7: paying it at 7 life is suicide
        Game game = play(7, "Myr Battlesphere");
        Player ai = game.getRegisteredPlayers().get(1);
        assertEquals(ai.getLife(), 7, "no life paid");
        assertTrue(has(ai, ZoneType.Hand, "Myr Battlesphere"), "kept in hand");
    }

    @Test
    public void testPicksTheOneItCanPay() {
        // read as 0, the 7-drop looked payable and was picked as the best card, then refused: nothing cast at all
        Game game = play(7, "Myr Battlesphere", "Steel Overseer");
        Player ai = game.getRegisteredPlayers().get(1);
        assertEquals(ai.getLife(), 5, "pays 2 for the cheap one");
        assertTrue(has(ai, ZoneType.Battlefield, "Steel Overseer"), "cheap artifact cast");
        assertTrue(has(ai, ZoneType.Hand, "Myr Battlesphere"), "the 7-drop stays");
    }

    @Test
    public void testPaysWhenAffordable() {
        // same spell at 20 life: 7 life for a 7-mana artifact is the point of the card
        Game game = play(20, "Myr Battlesphere");
        Player ai = game.getRegisteredPlayers().get(1);
        assertEquals(ai.getLife(), 13, "pays exactly the mana value");
        assertFalse(has(ai, ZoneType.Hand, "Myr Battlesphere"), "cast");
    }
}
