package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 173, visionneur v211 (Kavaron - merfolk, tours 8 à 16) : six Islands, deux Eclipsed Merrow et
// Force of Will en main, merfolk n'a rien lancé pendant huit tours (règle 22 : cinq mana gardés pour Force of Will,
// qui se lance gratuitement en exilant une carte bleue).
// Contre-épreuve : sans autre carte bleue à exiler, Force of Will garde son mana et la créature attend.
public class FreeCounter173Test extends AITest {

    private Game game(final int warriors) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        for (int i = 0; i < 5; i++) {
            addCard("Island", ai);
        }
        for (int i = 0; i < warriors; i++) {
            addCardToZone("Phantom Warrior", ai, ZoneType.Hand);
        }
        addCardToZone("Force of Will", ai, ZoneType.Hand);
        // rule 174: a counterspell deck, not twenty bears (an aggressive deck deploys first)
        for (int i = 0; i < 20; i++) {
            addCardToZone("Island", ai, ZoneType.Library);
        }
        fillLibrary(opponent, 20);
        ai.addLandPlayedThisTurn();
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        return game;
    }

    @Test(timeOut = 120000)
    public void testCastsWithABlueCardToPitch() {
        Game game = game(2);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Phantom Warrior", ZoneType.Battlefield), 1, gameStateToString(game));
    }

    @Test(timeOut = 120000)
    public void testKeepsManaWithoutAPitchCard() {
        Game game = game(1);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Phantom Warrior", ZoneType.Battlefield), 0, gameStateToString(game));
    }
}
