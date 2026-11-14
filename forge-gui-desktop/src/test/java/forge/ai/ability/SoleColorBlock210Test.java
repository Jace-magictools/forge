package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.ai.SoleColorAi;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.CounterEnumType;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

// magic-tools rules 210-211 (tester 05/11, Pod a test game). 210: the only source of a color a spell in
// hand needs does not pay a sacrifice. 211: a creature that comes back from the graveyard blocks.
public class SoleColorBlock210Test extends AITest {

    private Player aiOf(Game game) {
        return game.getPlayers().get(1);
    }

    @Test(timeOut = 120000)
    public void testPodKeepsTheOnlyBlackSource() {
        Game game = initAndCreateGame();
        Player ai = aiOf(game);
        Player opponent = game.getPlayers().get(0);
        addCard("Forest", ai);
        addCard("Forest", ai);
        addCard("Forest", ai);
        Card birds = addCard("Birds of Paradise", ai);
        birds.setSickness(false);
        addCard("Birthing Pod", ai);
        addCardToZone("Murderous Redcap", ai, ZoneType.Hand);
        addCardToZone("Murderous Redcap", ai, ZoneType.Hand);
        addCardToZone("Safehold Elite", ai, ZoneType.Library);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        assertTrue(SoleColorAi.keepsColor(ai, birds));
        playUntilNextTurn(game);
        assertEquals(birds.getZone().getZoneType(), ZoneType.Battlefield);
    }

    @Test(timeOut = 120000)
    public void testAnotherSourceOrNoNeedFreesIt() {
        Game game = initAndCreateGame();
        Player ai = aiOf(game);
        Card birds = addCard("Birds of Paradise", ai);
        addCardToZone("Murderous Redcap", ai, ZoneType.Hand);
        assertTrue(SoleColorAi.keepsColor(ai, birds));
        // a black land in hand replaces it
        Card swamp = addCardToZone("Swamp", ai, ZoneType.Hand);
        assertFalse(SoleColorAi.keepsColor(ai, birds));
        game.getAction().moveToGraveyard(swamp, null);
        // a Mountain pays the hybrid {B/R} of the Redcap
        addCard("Mountain", ai);
        assertFalse(SoleColorAi.keepsColor(ai, birds));
    }

    private Card blockGame(String blockerName, String attackerName, boolean counter) {
        Game game = initAndCreateGame();
        Player ai = aiOf(game);
        Player opponent = game.getPlayers().get(0);
        Card blocker = addCard(blockerName, ai);
        blocker.setSickness(false);
        if (counter) {
            blocker.addCounterInternal(CounterEnumType.M1M1, 1, ai, false, null, null);
        }
        Card attacker = addCard(attackerName, opponent);
        attacker.setSickness(false);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getAction().checkStateEffects(true);
        forge.game.combat.Combat combat = new forge.game.combat.Combat(opponent);
        combat.addAttacker(attacker, ai);
        new forge.ai.AiBlockController(ai, ai).assignBlockersForCombat(combat);
        return combat.getBlockers(attacker).isEmpty() ? null : combat.getBlockers(attacker).get(0);
    }

    @Test(timeOut = 120000)
    public void testPersistBlocks() {
        assertEquals(blockGame("Safehold Elite", "Hill Giant", false).getName(), "Safehold Elite");
    }

    @Test(timeOut = 120000)
    public void testPersistWithCounterDoesNotChump() {
        assertEquals(blockGame("Safehold Elite", "Hill Giant", true), null);
    }
}
