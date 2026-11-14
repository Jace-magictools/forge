package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 169, relevé « mana perdu » v207 (Gwyll - Chevalier, tours 12 à 24) : Chevalier gardait Worthy Knight
// et Inspiring Veteran avec 7 terrains ; rule 93 ne voyait ni le seigneur de tribu ni le déclencheur « whenever you cast »
public class HoldBack169Test extends AITest {

    private Game setup(final String inPlay, final String inHand) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        for (int i = 0; i < 3; i++) {
            addCard("Plains", ai);
            addCard("Mountain", ai);
        }
        addCard("Youthful Knight", ai);
        addCard("Youthful Knight", ai);
        addCard(inPlay, ai);
        addCardToZone(inHand, ai, ZoneType.Hand);
        for (int i = 0; i < 4; i++) {
            addCard("Plains", opponent);
        }
        addCardToZone("Wrath of God", opponent, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        ai.setLandsPlayedThisTurn(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        return game;
    }

    @Test
    public void testTribalLordCast() {
        Game game = setup("Youthful Knight", "Inspiring Veteran");
        assertEquals(countCardsWithName(game, "Inspiring Veteran", ZoneType.Battlefield), 1, gameStateToString(game));
    }

    @Test
    public void testCastTriggerPayoffFires() {
        Game game = setup("Worthy Knight", "Youthful Knight");
        assertEquals(countCardsWithName(game, "Youthful Knight", ZoneType.Battlefield), 3, gameStateToString(game));
    }

    @Test
    public void testPlainBodyStillHeld() {
        Game game = setup("Youthful Knight", "Grizzly Bears");
        assertEquals(countCardsWithName(game, "Grizzly Bears", ZoneType.Hand), 1, gameStateToString(game));
    }
}
