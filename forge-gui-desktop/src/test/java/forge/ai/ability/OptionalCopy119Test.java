package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 119, an optional "enter as a copy" with nothing to copy doesn't stop the creature from being cast
public class OptionalCopy119Test extends AITest {

    private Game run(String land, String creature) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        for (int i = 0; i < 4; i++) {
            addCard(land, ai);
        }
        addCardToZone(creature, ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, opponent);
        game.getAction().checkStateEffects(true);
        playUntilNextTurn(game);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        return game;
    }

    // Ezri Dax (2/2 flash, may enter as a copy of a creature card put into our graveyard this turn): cast as a 2/2
    @Test
    public void testCastsEzriWithoutCopy() {
        Game game = run("Plains", "Ezri Dax, Newly Joined");
        assertEquals(countCardsWithName(game, "Ezri Dax, Newly Joined", ZoneType.Battlefield), 1, gameStateToString(game));
    }

    // Phantasmal Image is a 0/0 without a copy: still kept in hand when there is nothing to copy
    @Test
    public void testKeepsZeroBodyClone() {
        Game game = run("Island", "Phantasmal Image");
        assertEquals(countCardsWithName(game, "Phantasmal Image", ZoneType.Hand), 1, gameStateToString(game));
    }
}
