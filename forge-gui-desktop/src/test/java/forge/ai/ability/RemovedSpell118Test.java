package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 118, an instant or sorcery marked AI:RemoveDeck:All with a simple effect is cast
public class RemovedSpell118Test extends AITest {

    private Game run(String land, String spell, String bear, String victim) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        for (int i = 0; i < 3; i++) {
            addCard(land, ai);
            addCard(bear, ai);
        }
        addCard(victim, opponent);
        addCardToZone(spell, ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, opponent);
        game.getAction().checkStateEffects(true);
        playUntilNextTurn(game);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        return game;
    }

    // X = 3 creatures: kills the 3/3
    @Test
    public void testCastsHobbitsSting() {
        Game game = run("Plains", "Hobbit's Sting", "Grizzly Bears", "Hill Giant");
        assertEquals(countCardsWithName(game, "Hill Giant", ZoneType.Battlefield), 0, gameStateToString(game));
    }

    // no creature of ours: X = 0, kept
    @Test
    public void testKeepsStingWithoutCreatures() {
        Game game = run("Plains", "Hobbit's Sting", "Plains", "Hill Giant");
        assertEquals(countCardsWithName(game, "Hobbit's Sting", ZoneType.Hand), 1, gameStateToString(game));
    }

    // an extra sacrifice cost stays out of reach (Life's Legacy, Sacrifice a creature)
    @Test
    public void testSacrificeCostStaysRemoved() {
        Game game = run("Forest", "Life's Legacy", "Grizzly Bears", "Hill Giant");
        assertEquals(countCardsWithName(game, "Life's Legacy", ZoneType.Hand), 1, gameStateToString(game));
    }
}
