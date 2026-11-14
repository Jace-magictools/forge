package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools, règle 204 : le seul sacrificateur ne paie pas Eldritch Evolution quand une créature à persistance attend
public class SacOutletKeep204Test extends AITest {

    private Game evolutionGame(String... extra) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        for (int i = 0; i < 3; i++) {
            addCard("Forest", ai);
        }
        addCard("Viscera Seer", ai).setSickness(false);
        for (String name : extra) {
            addCard(name, ai).setSickness(false);
        }
        addCardToZone("Eldritch Evolution", ai, ZoneType.Hand);
        addCardToZone("Grizzly Bears", ai, ZoneType.Library);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        return game;
    }

    private int evolutionsCast(Game game) {
        playUntilPhase(game, PhaseType.END_OF_TURN);
        return countCardsWithName(game, "Eldritch Evolution", ZoneType.Exile);
    }

    // le retour de tester : Seer seule créature, Murderous Redcap en main → l'Évolution attend
    @Test
    public void testSeerGardeeAvecPersistanceEnMain() {
        Game game = evolutionGame();
        addCardToZone("Murderous Redcap", game.getPlayers().get(1), ZoneType.Hand);
        assertEquals(evolutionsCast(game), 0, gameStateToString(game));
        assertEquals(countCardsWithName(game, "Viscera Seer", ZoneType.Battlefield), 1);
    }

    // à valeur de mana égale, c'est l'autre créature qui part, jamais la Seer
    @Test
    public void testAutreCreatureSacrifiee() {
        Game game = evolutionGame("Savannah Lions");
        addCardToZone("Murderous Redcap", game.getPlayers().get(1), ZoneType.Hand);
        assertEquals(evolutionsCast(game), 1, gameStateToString(game));
        assertEquals(countCardsWithName(game, "Viscera Seer", ZoneType.Battlefield), 1);
        assertEquals(countCardsWithName(game, "Savannah Lions", ZoneType.Battlefield), 0);
    }

    // sans pièce de boucle, la Seer reste une 1/1 qu'on peut monter
    @Test
    public void testSansBoucleLaSeerPaie() {
        Game game = evolutionGame();
        assertEquals(evolutionsCast(game), 1, gameStateToString(game));
    }
}
