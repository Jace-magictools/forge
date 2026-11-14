package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 231, Toxico (6/11): White Sun's Twilight held six turns, six free lands, life 21 to 5, the opponent
// without creature: « if X is 5 or more, destroy all other creatures » was judged as a sweep even at X = 4
public class ConditionalSweep231Test extends AITest {

    private static final String MITE = "Phyrexian Mite Token";

    private Game play(final int lands, final String ours, final String theirs) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        for (int i = 0; i < lands; i++) {
            addCard("Plains", ai);
        }
        if (ours != null) {
            addCard(ours, ai);
        }
        if (theirs != null) {
            for (int i = 0; i < 3; i++) {
                addCard(theirs, opponent);
            }
        }
        addCardToZone("White Sun's Twilight", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        ai.setLandsPlayedThisTurn(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        return game;
    }

    @Test
    public void testConditionNotMetDoesNotVeto() {
        Game game = play(6, null, null);
        assertEquals(countCardsWithName(game, MITE, ZoneType.Battlefield), 4, gameStateToString(game));
    }

    @Test
    public void testNothingOfOursToSweepKeepsTheBiggestX() {
        Game game = play(9, null, null);
        assertEquals(countCardsWithName(game, MITE, ZoneType.Battlefield), 7, gameStateToString(game));
    }

    @Test
    public void testXStopsUnderTheSweepThatWouldHitOnlyUs() {
        Game game = play(9, "Serra Angel", null);
        assertEquals(countCardsWithName(game, "Serra Angel", ZoneType.Battlefield), 1, gameStateToString(game));
        assertEquals(countCardsWithName(game, MITE, ZoneType.Battlefield), 4, gameStateToString(game));
    }

    @Test
    public void testSweepStaysWhenItHitsThem() {
        Game game = play(9, null, "Serra Angel");
        assertEquals(countCardsWithName(game, "Serra Angel", ZoneType.Battlefield), 0, gameStateToString(game));
        assertEquals(countCardsWithName(game, MITE, ZoneType.Battlefield), 7, gameStateToString(game));
    }
}
