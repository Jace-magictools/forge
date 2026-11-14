package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.ai.MinorPermanentAi;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

// magic-tools: rule 194, Lost to Legend went on Chromatic Star (a tester report 2/11 14:57)
public class MinorPermanent194Test extends AITest {

    private static boolean has(Player p, ZoneType zone, String name) {
        return p.getCardsIn(zone).anyMatch(c -> c.getName().equals(name));
    }

    private Game play(String spell, String land, String... theirs) {
        Game game = initAndCreateGame();
        Player opp = game.getPlayers().get(0);
        Player ai = game.getPlayers().get(1);
        for (int i = 0; i < 5; i++) {
            addCard(land, ai);
        }
        addCardToZone(spell, ai, ZoneType.Hand);
        for (String c : theirs) {
            Card card = addCard(c, opp);
            card.setTapped(true);
        }
        fillLibrary(opp, 20);
        fillLibrary(ai, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        // an instant waits for the end of the opponent's turn: play through it
        playUntilPhase(game, PhaseType.END_OF_TURN);
        playUntilNextTurn(game);
        playUntilNextTurn(game);
        return game;
    }

    @Test
    public void testLostToLegendKeptFromChromaticStar() {
        Game game = play("Lost to Legend", "Plains", "Chromatic Star");
        Player ai = game.getRegisteredPlayers().get(1);
        assertTrue(has(ai, ZoneType.Hand, "Lost to Legend"), "kept in hand");
        assertTrue(has(game.getRegisteredPlayers().get(0), ZoneType.Battlefield, "Chromatic Star"));
    }

    @Test
    public void testLostToLegendGoesOnTheOneRingNotTheStar() {
        Game game = play("Lost to Legend", "Plains", "Chromatic Star", "The One Ring");
        Player opp = game.getRegisteredPlayers().get(0);
        assertFalse(has(opp, ZoneType.Battlefield, "The One Ring"), "the Ring goes");
        assertTrue(has(opp, ZoneType.Battlefield, "Chromatic Star"), "the Star stays");
    }

    @Test
    public void testLostToLegendStillHitsSolRing() {
        Game game = play("Lost to Legend", "Plains", "Sol Ring");
        assertFalse(has(game.getRegisteredPlayers().get(0), ZoneType.Battlefield, "Sol Ring"), "repeatable mana is a target");
    }

    @Test
    public void testDisenchantKeptFromExpeditionMap() {
        Game game = play("Disenchant", "Plains", "Expedition Map");
        assertTrue(has(game.getRegisteredPlayers().get(1), ZoneType.Hand, "Disenchant"), "kept in hand");
    }

    @Test
    public void testWhatStaysATarget() {
        Game game = initAndCreateGame();
        Player opp = game.getPlayers().get(0);
        assertFalse(MinorPermanentAi.isMinor(addCard("Pacifism", opp)), "static effect");
        assertFalse(MinorPermanentAi.isMinor(addCard("Oblivion Stone", opp)), "destroys the board");
        assertFalse(MinorPermanentAi.isMinor(addCard("Mind Stone", opp)), "mana without a sacrifice");
        assertTrue(MinorPermanentAi.isMinor(addCard("Chromatic Star", opp)));
        assertTrue(MinorPermanentAi.isMinor(addCard("Wayfarer's Bauble", opp)));
    }
}
