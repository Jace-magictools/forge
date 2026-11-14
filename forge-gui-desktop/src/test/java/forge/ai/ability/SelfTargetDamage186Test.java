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

// magic-tools: rule 186 (D112), mandatory "N damage to you or another target creature you control" (Orque Berserk):
// a creature that survives takes it; none survives and life stays comfortable, our face; low life, the cheapest creature.
public class SelfTargetDamage186Test extends AITest {

    private SpellAbility berserk(Game game, Player ai, int life, String... creatures) {
        ai.setLife(life, null);
        Card host = addCard("Hill Giant", ai);
        for (String c : creatures) {
            addCard(c, ai);
        }
        SpellAbility sa = AbilityFactory.getAbility("DB$ DealDamage | ValidTgts$ You,Creature.Other+YouCtrl"
                + " | TgtPrompt$ Select you or another target creature you control | NumDmg$ 3", host);
        sa.setActivatingPlayer(ai);
        game.getPhaseHandler().devModeSet(PhaseType.END_OF_TURN, ai);
        game.getAction().checkStateEffects(true);
        SpellApiToAi.Converter.get(sa.getApi()).doTriggerNoCostWithSubs(ai, sa, true);
        return sa;
    }

    @Test
    public void testComfortableFace() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        SpellAbility sa = berserk(game, ai, 17, "Hill Giant");
        assertEquals(sa.getTargets().size(), 1, "one target");
        assertTrue(sa.getTargets().contains(ai), "face: " + sa.getTargets());
    }

    @Test
    public void testSurvivorTakesIt() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        SpellAbility sa = berserk(game, ai, 17, "Craw Wurm");
        assertEquals(sa.getTargetCard() == null ? "" : sa.getTargetCard().getName(), "Craw Wurm");
    }

    @Test
    public void testLowLifeCheapestCreature() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        SpellAbility sa = berserk(game, ai, 4, "Grizzly Bears", "Hill Giant");
        assertEquals(sa.getTargetCard() == null ? "" : sa.getTargetCard().getName(), "Grizzly Bears");
    }
}
