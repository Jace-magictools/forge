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

// magic-tools: rule 217, Arc Lightning killed a blank Soldier token and left Soul Warden alive
public class BlankToken217Test extends AITest {

    private static boolean has(Player p, ZoneType zone, String name) {
        return p.getCardsIn(zone).anyMatch(c -> c.getName().equals(name));
    }

    private Game play(String spell, String[] theirCards, String... theirTokens) {
        Game game = initAndCreateGame();
        Player opp = game.getPlayers().get(0);
        Player ai = game.getPlayers().get(1);
        for (int i = 0; i < 3; i++) {
            addCard("Mountain", ai);
        }
        addCardToZone(spell, ai, ZoneType.Hand);
        for (String c : theirCards) {
            addCard(c, opp).setSickness(false);
        }
        for (String t : theirTokens) {
            addToken(t, opp).setSickness(false);
        }
        fillLibrary(opp, 20);
        fillLibrary(ai, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        playUntilPhase(game, PhaseType.MAIN2);
        return game;
    }

    @Test
    public void testArcLightningKillsTheCardsNotTheBlankToken() {
        Game game = play("Arc Lightning", new String[] {"Soul Warden", "Cruel Celebrant"}, "w_1_1_soldier");
        Player opp = game.getRegisteredPlayers().get(0);
        Player ai = game.getRegisteredPlayers().get(1);
        assertFalse(has(ai, ZoneType.Hand, "Arc Lightning"), "cast");
        assertFalse(has(opp, ZoneType.Battlefield, "Soul Warden"), "warden dies");
        assertFalse(has(opp, ZoneType.Battlefield, "Cruel Celebrant"), "celebrant dies");
        assertEquals(opp.getCreaturesInPlay().size(), 1, "only the blank token stays");
    }

    @Test
    public void testATokenWithAKeywordKeepsItsPriority() {
        Game game = play("Forked Bolt", new String[] {"Soul Warden", "Soul Warden"}, "w_1_1_bird_flying");
        Player opp = game.getRegisteredPlayers().get(0);
        assertEquals(opp.getCreaturesInPlay().size(), 1, "two die");
        assertTrue(has(opp, ZoneType.Battlefield, "Soul Warden"), "the flying token goes first");
    }
}
