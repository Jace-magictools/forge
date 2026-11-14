package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.ai.AiAbilityDecision;
import forge.ai.SpellApiToAi;
import forge.game.Game;
import forge.game.ability.AbilityFactory;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

// magic-tools: rule 185 (Dino DNA copying Isperia every turn while a copy of Isperia was already ours): a legendary
// copy of a name we already control dies to the legend rule, so another target is taken, or none.
public class LegendCopy185Test extends AITest {

    private SpellAbility copy(Game game, Player ai, boolean ownLegend, String... oppCards) {
        Player opp = game.getPlayers().get(0);
        Card host = addCard("Ornithopter", ai);
        for (String c : oppCards) {
            addCard(c, opp);
        }
        if (ownLegend) {
            addCard("Isperia, Supreme Judge", ai);
        }
        SpellAbility sa = AbilityFactory.getAbility("AB$ CopyPermanent | Cost$ 0 | ValidTgts$ Creature.OppCtrl"
                + " | SetPower$ 6 | SetToughness$ 6 | AddKeywords$ Trample", host);
        sa.setActivatingPlayer(ai);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        return sa;
    }

    private AiAbilityDecision decide(Player ai, SpellAbility sa) {
        return SpellApiToAi.Converter.get(sa.getApi()).doTriggerNoCostWithSubs(ai, sa, false);
    }

    @Test
    public void testOnlyOurLegendNoCopy() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        SpellAbility sa = copy(game, ai, true, "Isperia, Supreme Judge");
        assertFalse(decide(ai, sa).willingToPlay(), "copy would die: " + sa.getTargets());
    }

    @Test
    public void testOtherTargetTaken() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        SpellAbility sa = copy(game, ai, true, "Isperia, Supreme Judge", "Grizzly Bears");
        assertTrue(decide(ai, sa).willingToPlay());
        assertEquals(sa.getTargetCard().getName(), "Grizzly Bears");
    }

    @Test
    public void testLegendCopiedWhenNotOurs() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        SpellAbility sa = copy(game, ai, false, "Isperia, Supreme Judge");
        assertTrue(decide(ai, sa).willingToPlay());
        assertEquals(sa.getTargetCard().getName(), "Isperia, Supreme Judge");
    }
}
