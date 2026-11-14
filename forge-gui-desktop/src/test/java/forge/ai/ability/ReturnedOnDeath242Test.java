package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.ai.PersistLoopAi;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.combat.Combat;
import forge.game.phase.PhaseType;
import forge.game.player.Player;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

// magic-tools, règle 242 (tester 06/11, a test game) : la créature que Luminous Broodmoth ou Nightmare Shepherd
// ramène bloque, elle revient et rejoue son entrée en jeu.
public class ReturnedOnDeath242Test extends AITest {

    private Player aiOf(Game game) {
        return game.getPlayers().get(1);
    }

    @Test(timeOut = 120000)
    public void testBroodmothBringsBackAGroundCreatureOnly() {
        Game game = initAndCreateGame();
        Player ai = aiOf(game);
        Card expert = addCard("Acquisitions Expert", ai);
        Card bird = addCard("Serra Avenger", ai);
        assertFalse(PersistLoopAi.comesBackFromBlock(ai, expert));
        addCard("Luminous Broodmoth", ai);
        game.getAction().checkStateEffects(true);
        assertTrue(PersistLoopAi.comesBackFromBlock(ai, expert));
        assertFalse(PersistLoopAi.comesBackFromBlock(ai, bird));
    }

    @Test(timeOut = 120000)
    public void testShepherdKeepsBigCreaturesAndTokensOut() {
        Game game = initAndCreateGame();
        Player ai = aiOf(game);
        Card expert = addCard("Acquisitions Expert", ai);
        Card big = addCard("Craw Wurm", ai);
        Card shepherd = addCard("Nightmare Shepherd", ai);
        Card token = addToken("c_1_1_a_servo", ai);
        game.getAction().checkStateEffects(true);
        assertTrue(PersistLoopAi.comesBackFromBlock(ai, expert));
        assertFalse(PersistLoopAi.comesBackFromBlock(ai, big));
        assertFalse(PersistLoopAi.comesBackFromBlock(ai, shepherd));
        assertFalse(PersistLoopAi.comesBackFromBlock(ai, token));
    }

    // revenir en enchantement ou en terrain n'est pas revenir pour bloquer
    @Test(timeOut = 120000)
    public void testBackAsSomethingElseDoesNotCount() {
        Game game = initAndCreateGame();
        Player ai = aiOf(game);
        Card vitality = addCard("Enduring Vitality", ai);
        Card ojer = addCard("Ojer Axonil, Deepest Might", ai);
        Card troll = addCard("Old-Growth Troll", ai);
        game.getAction().checkStateEffects(true);
        assertFalse(PersistLoopAi.comesBackFromBlock(ai, vitality));
        assertFalse(PersistLoopAi.comesBackFromBlock(ai, ojer));
        assertFalse(PersistLoopAi.comesBackFromBlock(ai, troll));
    }

    // l'adversaire nous tape pour 2 : la créature qui revient se met devant
    @Test(timeOut = 120000)
    public void testItBlocks() {
        Game game = initAndCreateGame();
        Player ai = aiOf(game);
        Player opponent = game.getPlayers().get(0);
        Card expert = addCard("Acquisitions Expert", ai);
        expert.setSickness(false);
        addCard("Luminous Broodmoth", ai).tap(true, null, null);
        Card attacker = addCard("Grizzly Bears", opponent);
        attacker.setSickness(false);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_ATTACKERS, opponent);
        game.getAction().checkStateEffects(true);
        Combat combat = new Combat(opponent);
        combat.addAttacker(attacker, ai);
        new forge.ai.AiBlockController(ai, ai).assignBlockersForCombat(combat);
        assertTrue(combat.getBlockers(attacker).contains(expert));
    }
}
