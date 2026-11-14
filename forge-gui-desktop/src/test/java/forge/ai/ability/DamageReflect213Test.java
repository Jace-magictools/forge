package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.ai.AiBlockController;
import forge.ai.DamageReflectAi;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.combat.Combat;
import forge.game.phase.PhaseType;
import forge.game.player.Player;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

// magic-tools rules 213-214 (tester 03/11, a test game): Lumra 17/17 blocks a 2/4 Donna Noble paired with
// Wayta, 34 damage come back. The block goes when it kills its own controller, stays when it costs little.
public class DamageReflect213Test extends AITest {

    private Card sickFree(String name, Player p) {
        Card c = addCard(name, p);
        c.setSickness(false);
        return c;
    }

    private boolean blocks(Card blocker, Card attacker, Player ai, Player opponent, Game game) {
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_ATTACKERS, opponent);
        game.getAction().checkStateEffects(true);
        Combat combat = new Combat(opponent);
        combat.addAttacker(attacker, ai);
        new AiBlockController(ai, ai).assignBlockersForCombat(combat);
        return combat.isBlocking(blocker);
    }

    @Test(timeOut = 120000)
    public void testReflectCountReadsTheScript() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        Card donna = sickFree("Donna Noble", opponent);
        assertEquals(DamageReflectAi.reflectCount(donna, ai), 1);
        assertEquals(DamageReflectAi.reflectCount(donna, opponent), 0);
        sickFree("Wayta, Trainer Prodigy", opponent);
        assertEquals(DamageReflectAi.reflectCount(donna, ai), 2);
        assertEquals(DamageReflectAi.reflectCount(sickFree("Boros Reckoner", opponent), ai), 2);
        // deals it to its own controller
        assertEquals(DamageReflectAi.reflectCount(sickFree("Jackal Pup", opponent), ai), 0);
        assertEquals(DamageReflectAi.reflectCount(sickFree("Grizzly Bears", opponent), ai), 0);
    }

    @Test(timeOut = 120000)
    public void testLethalBlockIsTakenBack() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        ai.setLife(5, null);
        Card donna = sickFree("Donna Noble", opponent);
        Card big = sickFree("Craw Wurm", ai);
        assertFalse(blocks(big, donna, ai, opponent, game));
    }

    @Test(timeOut = 120000)
    public void testHighLifeBlockStays() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        ai.setLife(40, null);
        Card donna = sickFree("Donna Noble", opponent);
        Card big = sickFree("Craw Wurm", ai);
        assertTrue(blocks(big, donna, ai, opponent, game));
    }

    @Test(timeOut = 120000)
    public void testAttacksWhenItsKillersWouldDie() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        Card donna = sickFree("Donna Noble", ai);
        Card wurm = sickFree("Craw Wurm", opponent);
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_ATTACKERS, ai);
        Combat combat = new Combat(ai);
        opponent.setLife(6, null);
        assertTrue(DamageReflectAi.killersWouldDie(ai, donna, java.util.List.of(wurm), combat, opponent));
        opponent.setLife(20, null);
        assertFalse(DamageReflectAi.killersWouldDie(ai, donna, java.util.List.of(wurm), combat, opponent));
    }
}
