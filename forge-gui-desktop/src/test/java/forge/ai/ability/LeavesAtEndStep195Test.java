package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.ai.LeavesAtEndStepAi;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

// magic-tools: rule 195, Reinforced Ronin cast in front of 4/3 blockers (tester 2/11 14:09)
public class LeavesAtEndStep195Test extends AITest {

    private static boolean has(Player p, ZoneType zone, String name) {
        return p.getCardsIn(zone).anyMatch(c -> c.getName().equals(name));
    }

    private Game play(String spell, String... theirs) {
        Game game = initAndCreateGame();
        Player opp = game.getPlayers().get(0);
        Player ai = game.getPlayers().get(1);
        addCard("Mountain", ai);
        addCardToZone(spell, ai, ZoneType.Hand);
        for (String c : theirs) {
            addCard(c, opp);
        }
        fillLibrary(opp, 20);
        fillLibrary(ai, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        playUntilPhase(game, PhaseType.COMBAT_END);
        return game;
    }

    @Test
    public void testRoninHeldBehindBiggerBlockers() {
        Game game = play("Reinforced Ronin", "Hill Giant", "Hill Giant");
        Player ai = game.getPlayers().get(1);
        assertTrue(has(ai, ZoneType.Hand, "Reinforced Ronin"), "stays in hand");
        assertFalse(has(ai, ZoneType.Battlefield, "Reinforced Ronin"));
    }

    @Test
    public void testRoninCastOnEmptyBoardAndAttacks() {
        Game game = play("Reinforced Ronin");
        Player ai = game.getPlayers().get(1);
        assertTrue(has(ai, ZoneType.Battlefield, "Reinforced Ronin"), "cast before combat");
        assertTrue(game.getPlayers().get(0).getLife() < 20, "and it attacked");
    }

    @Test
    public void testCreatureThatStaysStillCastBehindBlocker() {
        Game game = play("Goblin Arsonist", "Hill Giant");
        playUntilPhase(game, PhaseType.END_OF_TURN); // no haste: cast in the second main phase
        Player ai = game.getPlayers().get(1);
        assertTrue(has(ai, ZoneType.Battlefield, "Goblin Arsonist"), "a creature that stays is still cast");
    }

    @Test
    public void testScopeReadsScripts() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        for (String yes : new String[] {"Reinforced Ronin", "Ball Lightning", "Viashino Sandscout", "Spark Elemental"}) {
            assertTrue(LeavesAtEndStepAi.applies(addCard(yes, ai)), yes);
        }
        for (String no : new String[] {"Goblin Arsonist", "Raging Goblin", "Krovikan Horror"}) {
            assertFalse(LeavesAtEndStepAi.applies(addCard(no, ai)), no);
        }
    }
}
