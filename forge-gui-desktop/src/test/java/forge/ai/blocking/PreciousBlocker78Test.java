package forge.ai.blocking;

import java.util.List;

import forge.ai.PlayerControllerAi;
import forge.ai.PreciousBlockerAi;
import forge.ai.simulation.SimulationTest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.CounterEnumType;
import forge.game.combat.Combat;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import org.testng.annotations.Test;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

// magic-tools: rule 78 (a tester report 29/10 13:13, Gwyll - Avacyn): Ajani's Pridemate and Righteous Valkyrie
// double-blocked Voracious Hatchling, which killed the Valkyrie ("the Valkyrie is very precious")
public class PreciousBlocker78Test extends SimulationTest {

    private List<Card> blockersOfHatchling(boolean withSerra) {
        Game game = initAndCreateGame();
        Player attacker = game.getPlayers().get(1);
        Player defender = game.getPlayers().get(0);
        defender.setLife(15, null);

        Card hatchling = addCard("Voracious Hatchling", attacker);
        hatchling.addCounterInternal(CounterEnumType.M1M1, 2, attacker, false, null, null);
        hatchling.setSickness(false);
        addCard("Ajani's Pridemate", defender);
        addCard("Righteous Valkyrie", defender);
        if (withSerra) {
            addCard("Serra Avenger", defender);
        }

        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_ATTACKERS, attacker);
        game.getAction().checkStateEffects(true);
        Combat combat = new Combat(attacker);
        combat.addAttacker(hatchling, defender);
        game.getPhaseHandler().setCombat(combat);
        ((PlayerControllerAi) defender.getController()).getAi().declareBlockersFor(defender, combat);
        return combat.getBlockers(hatchling);
    }

    @Test
    public void testValkyrieDoesNotJoinTheGang() {
        for (int i = 0; i < 6; i++) {
            List<Card> blockers = blockersOfHatchling(false);
            assertFalse(blockers.stream().anyMatch(c -> c.getName().equals("Righteous Valkyrie")), blockers.toString());
            blockers = blockersOfHatchling(true);
            assertFalse(blockers.stream().anyMatch(c -> c.getName().equals("Righteous Valkyrie")), blockers.toString());
        }
    }

    @Test
    public void testEngineBonus() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        Card valkyrie = addCard("Righteous Valkyrie", p);
        Card pridemate = addCard("Ajani's Pridemate", p);
        Card bears = addCard("Grizzly Bears", p);
        addCard("Serra Avenger", p);
        game.getAction().checkStateEffects(true);
        // +2/+2 to three other creatures, halved (conditional), plus the trigger on other Angels and Clerics
        assertTrue(PreciousBlockerAi.engineBonus(p, valkyrie) >= 100, "" + PreciousBlockerAi.engineBonus(p, valkyrie));
        assertTrue(PreciousBlockerAi.engineBonus(p, pridemate) > 0);
        assertTrue(PreciousBlockerAi.engineBonus(p, bears) == 0);
    }
}
