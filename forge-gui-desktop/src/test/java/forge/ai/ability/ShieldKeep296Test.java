package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools, règle 296 : Birthing Pod ne sacrifie pas Melira, seul bouclier, quand une créature à persistance attend
public class ShieldKeep296Test extends AITest {

    private Game evolutionGame(String... extra) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        for (int i = 0; i < 3; i++) {
            addCard("Forest", ai);
        }
        addCard("Melira, Sylvok Outcast", ai).setSickness(false);
        for (String name : extra) {
            addCard(name, ai).setSickness(false);
        }
        addCard("Birthing Pod", ai);
        addCardToZone("Centaur Courser", ai, ZoneType.Library);
        addCardToZone("Grizzly Bears", ai, ZoneType.Library);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        return game;
    }

    private int podsActivated(Game game) {
        playUntilPhase(game, PhaseType.END_OF_TURN);
        return countCardsWithName(game, "Savannah Lions", ZoneType.Graveyard) + countCardsWithName(game, "Melira, Sylvok Outcast", ZoneType.Graveyard);
    }

    // Melira et une autre créature, Murderous Redcap en main : c'est l'autre qui part, Melira reste
    @Test
    public void testMeliraGardee() {
        Game game = evolutionGame("Savannah Lions");
        addCardToZone("Murderous Redcap", game.getPlayers().get(1), ZoneType.Hand);
        assertEquals(podsActivated(game), 1, gameStateToString(game));
        assertEquals(countCardsWithName(game, "Melira, Sylvok Outcast", ZoneType.Battlefield), 1, gameStateToString(game));
        assertEquals(countCardsWithName(game, "Savannah Lions", ZoneType.Battlefield), 0);
    }

    // sans créature à persistance, Melira est une 2/2 qu'on peut monter
    @Test
    public void testSansPersistanceMeliraPaie() {
        Game game = evolutionGame("Savannah Lions");
        assertEquals(podsActivated(game), 1, gameStateToString(game));
        assertEquals(countCardsWithName(game, "Melira, Sylvok Outcast", ZoneType.Battlefield), 0, gameStateToString(game));
    }
}
