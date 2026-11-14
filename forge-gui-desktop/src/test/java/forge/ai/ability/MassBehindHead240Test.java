package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 240, relevé v273 (Hour of Devastation refused at 4 life, no creature, 4 opposing creatures; Time Wipe
// refused with an empty board of ours): the head of the spell vetoed a sweep that was never judged
public class MassBehindHead240Test extends AITest {

    private Game setup(String spell, String[] ours, String[] theirs, String... lands) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        for (String land : lands) {
            addCard(land, ai);
        }
        addCardToZone(spell, ai, ZoneType.Hand);
        for (String c : ours) {
            addCard(c, ai);
        }
        for (String c : theirs) {
            addCard(c, opponent);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        ai.setLandsPlayedThisTurn(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        return game;
    }

    private static final String[] MOUNTAINS = {"Mountain", "Mountain", "Mountain", "Mountain", "Mountain"};
    private static final String[] AZORIUS = {"Plains", "Plains", "Island", "Plains", "Island"};
    private static final String[] NONE = {};
    private static final String[] TWO = {"Hill Giant", "Grizzly Bears"};

    @Test
    public void testHourSweepsWhenBehind() {
        Game game = setup("Hour of Devastation", NONE, TWO, MOUNTAINS);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Hill Giant", ZoneType.Battlefield), 0, gameStateToString(game));
    }

    @Test
    public void testHourKeptWhenAhead() {
        Game game = setup("Hour of Devastation", new String[] {"Shivan Dragon", "Hill Giant", "Grizzly Bears"}, new String[] {"Grizzly Bears"}, MOUNTAINS);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Hour of Devastation", ZoneType.Hand), 1, gameStateToString(game));
    }

    @Test
    public void testHourKeptOnEmptyBoard() {
        Game game = setup("Hour of Devastation", NONE, NONE, MOUNTAINS);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Hour of Devastation", ZoneType.Hand), 1, gameStateToString(game));
    }

    @Test
    public void testPileupKeptOnEmptyBoard() {
        Game game = setup("Spectacular Pileup", NONE, NONE, "Mountain", "Mountain", "Mountain", "Mountain");
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Spectacular Pileup", ZoneType.Hand), 1, gameStateToString(game));
    }

    @Test
    public void testTimeWipeSweepsWithEmptyBoardOfOurs() {
        Game game = setup("Time Wipe", NONE, TWO, AZORIUS);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Hill Giant", ZoneType.Battlefield), 0, gameStateToString(game));
    }

    @Test
    public void testTimeWipeSavesOurCreature() {
        Game game = setup("Time Wipe", new String[] {"Grizzly Bears"}, new String[] {"Shivan Dragon", "Hill Giant", "Hill Giant"}, AZORIUS);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Shivan Dragon", ZoneType.Battlefield), 0, gameStateToString(game));
        assertEquals(countCardsWithName(game, "Grizzly Bears", ZoneType.Hand), 1, gameStateToString(game));
    }

    @Test
    public void testTimeWipeKeptWhenAhead() {
        Game game = setup("Time Wipe", new String[] {"Shivan Dragon", "Hill Giant"}, new String[] {"Grizzly Bears"}, AZORIUS);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Time Wipe", ZoneType.Hand), 1, gameStateToString(game));
    }
}
