package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

public class AttackTriggerMain1AiTest extends AITest {

    private Game setUp(final String attacker) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);

        addCards("Mountain", 2, ai);
        addCards("Plains", 2, ai);
        addCard(attacker, ai).setSickness(false);
        addCardToZone("Winota, Joiner of Forces", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 30);

        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        gameLoopUntilNextPhase(game);
        return game;
    }

    // a non-Human about to attack into an empty board: Winota comes down in main 1 to trigger on it
    @Test
    public void testAttackTriggerCreatureInMain1() {
        Game game = setUp("Grizzly Bears");
        assertEquals(countCardsWithName(game, "Winota, Joiner of Forces", ZoneType.Battlefield), 1);
    }

    // only a Human attacker, which does not trigger Winota: stock behaviour, she waits for main 2
    @Test
    public void testNoTriggeringAttackerWaitsForMain2() {
        Game game = setUp("Elite Vanguard");
        assertEquals(countCardsWithName(game, "Winota, Joiner of Forces", ZoneType.Battlefield), 0);
    }
}
