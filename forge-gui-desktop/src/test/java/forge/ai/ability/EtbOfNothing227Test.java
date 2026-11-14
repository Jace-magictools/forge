package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 227, relevé « mana perdu » v261 (Jurassic - Smeagol, tours 12 à 18) : Earthshaker Dreadmaw, 6/6 piétinement,
// gardé en main à 11 terrains : « pioche pour chaque autre Dinosaure » vaut 0, DrawAi refuse, et le corps ne sort jamais
public class EtbOfNothing227Test extends AITest {

    private Game setup(final String inHand, final int libraryAi) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        for (int i = 0; i < 7; i++) {
            addCard("Forest", ai);
        }
        addCardToZone(inHand, ai, ZoneType.Hand);
        for (int i = 0; i < 4; i++) {
            addCard("Island", opponent);
        }
        fillLibrary(ai, libraryAi);
        fillLibrary(opponent, 20);
        ai.setLandsPlayedThisTurn(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        return game;
    }

    @Test
    public void testDrawOfNothingKeepsTheBody() {
        Game game = setup("Earthshaker Dreadmaw", 20);
        assertEquals(countCardsWithName(game, "Earthshaker Dreadmaw", ZoneType.Battlefield), 1, gameStateToString(game));
    }

    @Test
    public void testLifeOfNothingKeepsTheBody() {
        // « gagne 1 point de vie par créature au cimetière », cimetière vide : même refus, même corps
        Game game = setup("Kraul Foragers", 20);
        assertEquals(countCardsWithName(game, "Kraul Foragers", ZoneType.Battlefield), 1, gameStateToString(game));
    }
}
