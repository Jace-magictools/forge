package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.spellability.AbilitySub;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.Trigger;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

// magic-tools: rule 138, Bones McCoy forced into mode 2 names our best creature, never the opponent's
public class Rescue138Test extends AITest {

    private static AbilitySub rescueMode(Card mccoy) {
        for (Trigger tr : mccoy.getTriggers()) {
            SpellAbility sa = tr.ensureAbility();
            if (sa == null) {
                continue;
            }
            for (AbilitySub sub : sa.getAdditionalAbilityList("Choices")) {
                if (Rescue121.isRescue(sub)) {
                    return sub;
                }
            }
        }
        return null;
    }

    private SpellAbility setup(Game[] out) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard("Craw Wurm", ai);
        addCard("Grizzly Bears", opponent);
        Card mccoy = addCard("Dr. Leonard \"Bones\" McCoy", ai);
        game.getAction().checkStateEffects(true);
        AbilitySub sub = rescueMode(mccoy);
        assertNotNull(sub);
        sub.setActivatingPlayer(ai);
        out[0] = game;
        return sub;
    }

    @Test
    public void testForcedNamesOurBest() {
        Game[] g = new Game[1];
        SpellAbility sub = setup(g);
        Player ai = g[0].getPlayers().get(1);
        assertTrue(Rescue121.decideTrigger(ai, sub, true).willingToPlay());
        assertEquals(sub.getTargets().size(), 1);
        assertEquals(sub.getTargets().getFirstTargetedCard().getName(), "Craw Wurm");
    }

    @Test
    public void testOptionalStaysEmpty() {
        Game[] g = new Game[1];
        SpellAbility sub = setup(g);
        Player ai = g[0].getPlayers().get(1);
        Rescue121.decideTrigger(ai, sub, false);
        assertEquals(sub.getTargets().size(), 0);
    }
}
