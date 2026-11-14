package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools, règle 16 : Neoform (sans AILogic) joue comme un Pod : sacrifie les Bears (2) pour le Centaur Courser (3)
public class SacUpgradeAiTest extends AITest {

    @Test
    public void testNeoformUpgradesBears() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard("Forest", ai);
        addCard("Island", ai);
        addCard("Grizzly Bears", ai).setSickness(false);
        addCardToZone("Neoform", ai, ZoneType.Hand);
        addCardToZone("Centaur Courser", ai, ZoneType.Library);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);

        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Centaur Courser", ZoneType.Battlefield), 1, gameStateToString(game));
        assertEquals(countCardsWithName(game, "Grizzly Bears", ZoneType.Graveyard), 1);
    }

    // rien de valeur + 1 dans la bibliothèque : Neoform reste en main, les Bears aussi
    @Test
    public void testNeoformKeptWithoutTarget() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard("Forest", ai);
        addCard("Island", ai);
        addCard("Grizzly Bears", ai).setSickness(false);
        addCardToZone("Neoform", ai, ZoneType.Hand);
        addCardToZone("Llanowar Elves", ai, ZoneType.Library);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);

        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Neoform", ZoneType.Hand), 1);
        assertEquals(countCardsWithName(game, "Grizzly Bears", ZoneType.Battlefield), 1);
    }
}
