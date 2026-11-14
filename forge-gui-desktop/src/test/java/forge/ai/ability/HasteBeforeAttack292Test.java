package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.ai.AiPlayDecision;
import forge.ai.PlayerControllerAi;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

// magic-tools rule 292 (plateau, Ugin 2d95dbcc7; tester 11/11: Uruk-hai captain cast, the Wargs could have given it
// haste): a creature that taps to give haste gives it in main 1 to a better creature that just arrived and would attack.
public class HasteBeforeAttack292Test extends AITest {

    private SpellAbility motivator(Game game, Player ai, boolean giant) {
        Player opponent = game.getPlayers().get(0);
        addCard("Grizzly Bears", opponent).setSickness(false);
        Card m = addCard("Goblin Motivator", ai);
        m.setSickness(false);
        addCard("Wall of Wood", ai).setSickness(false); // stays home to block
        if (giant) {
            addCard("Hill Giant", ai); // just arrived: summoning sick
        }
        for (int i = 0; i < 3; i++) {
            addCard("Mountain", ai);
        }
        SpellAbility sa = null;
        for (SpellAbility a : m.getSpellAbilities()) {
            if (a.isAbility()) {
                sa = a; // the {T} haste ability, not the creature spell
            }
        }
        sa.setActivatingPlayer(ai);
        return sa;
    }

    @Test
    public void testHasteGivenBeforeAttacks() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        SpellAbility sa = motivator(game, ai, true);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        assertTrue(PumpAi.hasteBeforeAttack292(ai, sa, game.getPhaseHandler()));
        AiPlayDecision d = ((PlayerControllerAi) ai.getController()).getAi().canPlaySa(sa);
        assertEquals(d, AiPlayDecision.WillPlay, "Goblin Motivator gives haste to Hill Giant in main 1");
        assertEquals(sa.getTargetCard().getName(), "Hill Giant");
    }

    @Test
    public void testNoSickCreatureNoHaste() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        SpellAbility sa = motivator(game, ai, false);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        assertFalse(PumpAi.hasteBeforeAttack292(ai, sa, game.getPhaseHandler()), "nobody to give haste to");
    }
}
