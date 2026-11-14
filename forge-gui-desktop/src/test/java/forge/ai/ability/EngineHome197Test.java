package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.ai.AiAttackController;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.combat.Combat;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

// magic-tools: rule 197, a tester reports 03/11 (ElfBall, a test game, a test game): a creature that draws off the
// spells in hand does not attack where the blocks can kill it.
public class EngineHome197Test extends AITest {

    private boolean attacks(Game game, Player ai, Card attacker) {
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_ATTACKERS, ai);
        game.getAction().checkStateEffects(true);
        Combat combat = new Combat(ai);
        new AiAttackController(ai).declareAttackers(combat);
        return combat.isAttacking(attacker);
    }

    private Card board(Game game, Player ai, Player opp, String engine) {
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        addCard("Black Knight", opp).setSickness(false);
        addCard("Hypnotic Specter", opp).setSickness(false);
        Card c = addCard(engine, ai);
        c.setSickness(false);
        return c;
    }

    /** a test game: Heritage Druid in hand, the double block kills the Archdruid */
    @Test
    public void testArchdruidStaysWithADruidInHand() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opp = game.getPlayers().get(0);
        Card c = board(game, ai, opp, "Gilt-Leaf Archdruid");
        addCardToZone("Heritage Druid", ai, ZoneType.Hand);
        assertFalse(attacks(game, ai, c), gameStateToString(game));
    }

    /** No druid left: nothing to protect, Forge's usual choice is untouched (no crash, the engine test says no) */
    @Test
    public void testNoFuelIsNotAnEngine() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Card c = addCard("Gilt-Leaf Archdruid", ai);
        addCardToZone("Forest", ai, ZoneType.Hand);
        addCardToZone("Lightning Bolt", ai, ZoneType.Hand);
        assertFalse(forge.ai.EngineHomeAi.keepHome(ai, c, java.util.List.of(), null, game.getPlayers().get(0)));
    }

    /** No opposing creature: the attack is free */
    @Test
    public void testNoBlockerAttacks() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Card c = addCard("Beast Whisperer", ai);
        c.setSickness(false);
        addCardToZone("Llanowar Elves", ai, ZoneType.Hand);
        assertTrue(attacks(game, ai, c), gameStateToString(game));
    }

    /** Two hits win: the engine goes */
    @Test
    public void testTwoHitsWin() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opp = game.getPlayers().get(0);
        Card c = board(game, ai, opp, "Gilt-Leaf Archdruid");
        addCardToZone("Heritage Druid", ai, ZoneType.Hand);
        opp.setLife(6, null);
        assertFalse(forge.ai.EngineHomeAi.keepHome(ai, c, opp.getCreaturesInPlay(), new Combat(ai), opp));
    }
}
