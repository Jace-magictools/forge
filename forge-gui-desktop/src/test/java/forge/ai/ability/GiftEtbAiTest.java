package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertTrue;

/** Rule 45: an ETB that only happens if the gift was promised doesn't keep the creature in hand when the gift isn't promised. */
public class GiftEtbAiTest extends AITest {

    private Game scrapshooter(boolean opponentArtifact) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard("Forest", ai);
        addCard("Forest", ai);
        addCard("Forest", ai);
        addCardToZone("Scrapshooter", ai, ZoneType.Hand);
        if (opponentArtifact) {
            addCard("Sol Ring", opponent);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        moveToMain2(game, ai);
        return game;
    }

    // no artifact or enchantment on the other side: the 4/4 reach is cast anyway (it was BadEtbEffects)
    @Test
    public void testScrapshooterCastWithoutTarget() {
        Game game = scrapshooter(false);
        Player ai = game.getPlayers().get(1);
        gameLoopUntilNextPhase(game);
        assertTrue(ai.getCardsIn(ZoneType.Battlefield).anyMatch(c -> c.getName().equals("Scrapshooter")), "Scrapshooter kept in hand");
    }

    @Test
    public void testScrapshooterCastWithTarget() {
        Game game = scrapshooter(true);
        Player ai = game.getPlayers().get(1);
        gameLoopUntilNextPhase(game);
        assertTrue(ai.getCardsIn(ZoneType.Battlefield).anyMatch(c -> c.getName().equals("Scrapshooter")), "Scrapshooter kept in hand");
    }
}
