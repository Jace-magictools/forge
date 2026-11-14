package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.combat.Combat;
import forge.game.phase.PhaseType;
import forge.game.player.Player;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

// magic-tools rule 290 (plateau, Ugin fae8a260e; tester 11/11: drums giving menace activated after blockers):
// menace and skulk are worth giving to an attacker before blocks, never after (flying, fear… already work that way).
public class EvasionAfterBlocks290Test extends AITest {

    private Object[] attack(PhaseType phase) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard("Hill Giant", opponent);
        Card bear = addCard("Grizzly Bears", ai);
        bear.setSickness(false);
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_ATTACKERS, ai);
        Combat combat = new Combat(ai);
        combat.addAttacker(bear, opponent);
        game.getPhaseHandler().setCombat(combat);
        if (phase != PhaseType.COMBAT_DECLARE_ATTACKERS) {
            game.getPhaseHandler().devModeSet(phase, ai, false);
        }
        game.getAction().checkStateEffects(true);
        return new Object[] {ai, bear};
    }

    @Test
    public void testMenaceBeforeBlocks() {
        Object[] o = attack(PhaseType.COMBAT_DECLARE_ATTACKERS);
        assertTrue(new PumpAi().isUsefulPumpKeyword((Player) o[0], "Menace", (Card) o[1], null, 0));
        assertTrue(new PumpAi().isUsefulPumpKeyword((Player) o[0], "Skulk", (Card) o[1], null, 0));
    }

    @Test
    public void testNoMenaceAfterBlocks() {
        Object[] o = attack(PhaseType.COMBAT_DECLARE_BLOCKERS);
        assertFalse(new PumpAi().isUsefulPumpKeyword((Player) o[0], "Menace", (Card) o[1], null, 0), "menace after blocks");
        assertFalse(new PumpAi().isUsefulPumpKeyword((Player) o[0], "Skulk", (Card) o[1], null, 0), "skulk after blocks");
    }
}
