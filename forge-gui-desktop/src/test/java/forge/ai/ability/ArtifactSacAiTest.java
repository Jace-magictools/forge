package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.CounterEnumType;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/** Kavaron: artifact sacrifice outlets (Atog, Arcbound Ravager) pumped for lethal, artifacts kept otherwise. */
public class ArtifactSacAiTest extends AITest {

    private Game atogGame(String outlet, int oppLife, boolean blocker) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        Card host = addCard(outlet, ai);
        host.setSickness(false);
        if (host.isArtifact()) {
            host.addCounterInternal(CounterEnumType.P1P1, 1, ai, false, null, null); // Modular 1
        }
        addCard("Ichor Wellspring", ai);
        addCard("Prized Statue", ai);
        addCard("Great Furnace", ai);
        if (blocker) {
            addCard("Grizzly Bears", opponent);
        }
        opponent.setLife(oppLife, null);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.MAIN2);
        return game;
    }

    // Atog 1/2 unblocked + 3 artifacts sacrificed = 7
    @Test
    public void testAtogSacrificesForLethal() {
        Game game = atogGame("Atog", 7, false);
        Player opponent = game.getPlayers().get(0);
        assertTrue(game.isGameOver() || opponent.getLife() <= 0, "Atog not pumped for lethal\n" + gameStateToString(game));
    }

    @Test
    public void testAtogKeepsArtifactsWhenNotLethal() {
        Game game = atogGame("Atog", 20, false);
        assertEquals(countCardsWithName(game, "Ichor Wellspring", ZoneType.Battlefield), 1, "artifact sacrificed for nothing\n" + gameStateToString(game));
    }

    // Arcbound Ravager 1/1 unblocked + 3 artifacts = 4
    @Test
    public void testRavagerSacrificesForLethal() {
        Game game = atogGame("Arcbound Ravager", 4, false);
        Player opponent = game.getPlayers().get(0);
        assertTrue(game.isGameOver() || opponent.getLife() <= 0, "Ravager not pumped for lethal\n" + gameStateToString(game));
    }

    // Forge used to sacrifice the Ravager to itself, and the land for a counter
    @Test
    public void testRavagerNeverEatsItself() {
        Game game = atogGame("Arcbound Ravager", 20, false);
        assertEquals(countCardsWithName(game, "Arcbound Ravager", ZoneType.Battlefield), 1, "Ravager sacrificed\n" + gameStateToString(game));
        assertEquals(countCardsWithName(game, "Great Furnace", ZoneType.Battlefield), 1, "land sacrificed for nothing\n" + gameStateToString(game));
    }

    // Weapons Manufacturing: Munitions tokens deal 2 when sacrificed to Atog
    @Test
    public void testAtogSacrificesMunitionsForLethal() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard("Atog", ai).setSickness(false);
        addCard("Weapons Manufacturing", ai);
        addToken("munitions", ai);
        addToken("munitions", ai);
        addCard("Grizzly Bears", opponent);
        opponent.setLife(4, null);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.MAIN2);
        assertTrue(game.isGameOver() || opponent.getLife() <= 0, "Munitions not sacrificed for lethal\n" + gameStateToString(game));
    }
}
