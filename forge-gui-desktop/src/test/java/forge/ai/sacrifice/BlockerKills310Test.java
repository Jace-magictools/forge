package forge.ai.sacrifice;

import forge.ai.FodderAi;
import forge.ai.simulation.SimulationTest;
import forge.game.Game;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.combat.Combat;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

// magic-tools: rule 310 (Smeagol v335 against Ajani): Gollum, Patient Plotter blocks Ajani's Pridemate, then Altar of
// Dementia sacrifices it before damage and the Pridemate survives. A blocker that would kill its attacker keeps blocking.
public class BlockerKills310Test extends SimulationTest {

    private Card fodder(String attackerName) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opp = game.getPlayers().get(0);
        Card altar = addCard("Altar of Dementia", ai);
        Card gollum = addCard("Gollum, Patient Plotter", ai);
        Card attacker = addCard(attackerName, opp);
        fillLibrary(ai, 20);
        fillLibrary(opp, 30);
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_BLOCKERS, opp);
        game.getAction().checkStateEffects(true);
        Combat combat = new Combat(opp);
        combat.addAttacker(attacker, ai);
        combat.addBlocker(attacker, gollum);
        game.getPhaseHandler().setCombat(combat);
        SpellAbility sa = altar.getSpellAbilities().stream().filter(x -> x.getApi() == ApiType.Mill).findFirst().get();
        sa.setActivatingPlayer(ai);
        return FodderAi.choose(ai, ai.getCreaturesInPlay(), sa);
    }

    @Test
    public void testBlockerThatKillsIsKept() {
        assertNull(fodder("Grizzly Bears")); // 3/1 against 2/2: the bears die if Gollum stays
    }

    @Test
    public void testChumpBlockerIsPaid() {
        Card c = fodder("Craw Wurm"); // 3/1 against 6/4: Gollum dies for nothing, the Altar may take it
        assertEquals(c == null ? null : c.getName(), "Gollum, Patient Plotter");
    }
}
