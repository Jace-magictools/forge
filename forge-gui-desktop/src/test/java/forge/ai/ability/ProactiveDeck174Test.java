package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 174, visionneur v212 (Poudlar - Infection, tours 3 à 9) : Swamp, Island, Plague Stinger et deux
// Corrupted Resolve en main, Infection n'a posé aucune créature (règle 22 : deux mana gardés pour le contre).
// Contre-épreuve : un deck de contres (autant de contres que de petites créatures) garde son mana.
public class ProactiveDeck174Test extends AITest {

    private Game game(final boolean counterDeck) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard("Swamp", ai);
        addCard("Island", ai);
        addCardToZone("Plague Stinger", ai, ZoneType.Hand);
        addCardToZone("Corrupted Resolve", ai, ZoneType.Hand);
        for (int i = 0; i < 10; i++) {
            addCardToZone(counterDeck ? "Counterspell" : "Runeclaw Bear", ai, ZoneType.Library);
            addCardToZone("Island", ai, ZoneType.Library);
        }
        fillLibrary(opponent, 20);
        ai.addLandPlayedThisTurn();
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        return game;
    }

    @Test(timeOut = 120000)
    public void testAggressiveDeckCastsItsCreature() {
        Game game = game(false);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Plague Stinger", ZoneType.Battlefield), 1, gameStateToString(game));
    }

    @Test(timeOut = 120000)
    public void testCounterDeckKeepsItsMana() {
        Game game = game(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Plague Stinger", ZoneType.Battlefield), 0, gameStateToString(game));
    }
}
