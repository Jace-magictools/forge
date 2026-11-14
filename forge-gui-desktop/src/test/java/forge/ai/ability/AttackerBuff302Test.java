package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 302 (tester 12/11, D170): an attacker that gets a +1/+1 counter when another creature of ours
// enters (Skyknight Squire, no BuffedBy hint) waits for the creature in hand: it is cast in main 1, before combat.
public class AttackerBuff302Test extends AITest {

    private Game setUp(final String attacker, final String inHand) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCards("Forest", 2, ai);
        addCard(attacker, ai).setSickness(false);
        addCardToZone(inHand, ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 30);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        gameLoopUntilNextPhase(game);
        return game;
    }

    @Test
    public void testCounterTriggerCreatureInMain1() {
        Game game = setUp("Skyknight Squire", "Grizzly Bears");
        assertEquals(countCardsWithName(game, "Grizzly Bears", ZoneType.Battlefield), 1, gameStateToString(game));
    }

    // a plain attacker gets nothing from the bear: stock behaviour, it waits for main 2
    @Test
    public void testPlainAttackerWaits() {
        Game game = setUp("Elite Vanguard", "Grizzly Bears");
        assertEquals(countCardsWithName(game, "Grizzly Bears", ZoneType.Battlefield), 0, gameStateToString(game));
    }
}
