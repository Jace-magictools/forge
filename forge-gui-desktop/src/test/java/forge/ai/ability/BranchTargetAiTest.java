package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertTrue;

/**
 * Rule 61: a Branch spell that carries its own target (Rancid Earth: "destroy target land; threshold: ...") never
 * chose a target, so it was never cast (v9 tournament: Rancid Earth seen in 142 Pox games, cast 0 times).
 * The AI of the branch that will resolve now picks the target.
 */
public class BranchTargetAiTest extends AITest {

    @Test
    public void testRancidEarthCast() {
        // fixed seed: the shared random state left by the other tests made this test fail in the full suite
        forge.util.MyRandom.setRandom(new java.util.Random(1));
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        // fixed profile: a random one (Reckless, Cautious...) made this test flaky in the full suite
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        addCard("Swamp", ai);
        addCard("Swamp", ai);
        addCard("Swamp", ai);
        addCardToZone("Rancid Earth", ai, ZoneType.Hand);
        addCard("Tropical Island", opponent);
        addCard("Forest", opponent);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        moveToMain2(game, ai);
        gameLoopUntilNextPhase(game);
        assertTrue(ai.getCardsIn(ZoneType.Graveyard).anyMatch(c -> c.getName().equals("Rancid Earth")),
                "Rancid Earth kept in hand");
    }
}
