package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.ai.SpellApiToAi;
import forge.game.Game;
import forge.game.ability.AbilityFactory;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

// magic-tools: rule 184 (D105), "2 damage to you or target creature you control" (Éclaireurs Orques): a creature that
// survives takes it rather than our face; none survives, we take it; it would kill us, the cheapest creature takes it.
public class ImposedDamage184Test extends AITest {

    private SpellAbility imposed(Game game, Player ai, int life, String... creatures) {
        ai.setLife(life, null);
        Card host = addCard("Raging Goblin", ai);
        for (String c : creatures) {
            addCard(c, ai);
        }
        host.setSVar("DBDamageYou", "DB$ DealDamage | Defined$ You | NumDmg$ 2 | ConditionDefined$ Targeted"
                + " | ConditionPresent$ Card | ConditionCompare$ EQ0");
        SpellAbility sa = AbilityFactory.getAbility("DB$ DealDamage | ValidTgts$ Creature.YouCtrl | TargetMin$ 0 | TargetMax$ 1"
                + " | NumDmg$ 2 | ConditionDefined$ Targeted | ConditionPresent$ Card | ConditionCompare$ GE1 | SubAbility$ DBDamageYou", host);
        sa.setActivatingPlayer(ai);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        SpellApiToAi.Converter.get(sa.getApi()).doTriggerNoCostWithSubs(ai, sa, true);
        return sa;
    }

    @Test
    public void testSurvivorTakesIt() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        SpellAbility sa = imposed(game, ai, 20, "Hill Giant");
        assertEquals(sa.getTargets().size(), 1, "one target");
        assertEquals(sa.getTargetCard().getName(), "Hill Giant");
    }

    @Test
    public void testNoSurvivorFace() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        SpellAbility sa = imposed(game, ai, 20, "Grizzly Bears");
        assertTrue(sa.getTargets().isEmpty(), "no target: " + sa.getTargets());
    }

    @Test
    public void testWouldDieCheapestCreature() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        SpellAbility sa = imposed(game, ai, 2, "Grizzly Bears");
        assertEquals(sa.getTargets().size(), 1, "one target");
        assertEquals(sa.getTargetCard().getName(), "Raging Goblin");
    }
}
