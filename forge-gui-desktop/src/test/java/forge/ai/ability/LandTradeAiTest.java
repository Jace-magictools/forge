package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 11, land for land (Raze in Casse Terrain was never cast)
public class LandTradeAiTest extends AITest {

    private Game razeGame(int ourLands, int theirLands, boolean landInHand) {
        return landDestructionGame("Raze", ourLands, theirLands, landInHand);
    }

    private Game landDestructionGame(String spell, int ourLands, int theirLands, boolean landInHand) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        addCards("Mountain", ourLands, ai);
        addCards("Plains", theirLands, opponent);
        addCardToZone(spell, ai, ZoneType.Hand);
        if (landInHand) {
            addCardToZone("Mountain", ai, ZoneType.Hand);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        return game;
    }

    @Test
    public void testRazeTradesLandWhenEven() {
        Game game = razeGame(3, 3, true);
        Player opponent = game.getPlayers().get(0);
        assertEquals(opponent.getLandsInPlay().size(), 2, "Raze not cast\n" + gameStateToString(game));
    }

    @Test
    public void testRazeEarlyDenial() {
        Game game = razeGame(2, 2, false);
        Player opponent = game.getPlayers().get(0);
        assertEquals(opponent.getLandsInPlay().size(), 1, "Raze not cast\n" + gameStateToString(game));
    }

    @Test
    public void testNoRazeWhenBehindOnLands() {
        Game game = razeGame(2, 4, false);
        Player opponent = game.getPlayers().get(0);
        assertEquals(opponent.getLandsInPlay().size(), 4, "Raze cast while behind\n" + gameStateToString(game));
    }

    // Forge held sorcery-speed land destruction at random (useRemovalNow): run 5 times, it must always be cast
    @Test
    public void testStoneRainNotHeld() {
        for (int i = 0; i < 5; i++) {
            Game game = landDestructionGame("Stone Rain", 3, 3, false);
            Player opponent = game.getPlayers().get(0);
            assertEquals(opponent.getLandsInPlay().size(), 2, "Stone Rain held\n" + gameStateToString(game));
        }
    }

    // rule 23: Strip Mine in a land destruction deck (Pox: 1 activation in 6 games, ~80 refusals)
    private Game stripGame(boolean landDestructionDeck) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        // four lands each and a land in hand: no mana lock (opponent above 3 lands), Forge kept Strip Mine
        addCards("Swamp", 3, ai);
        addCard("Strip Mine", ai);
        addCardToZone("Swamp", ai, ZoneType.Hand);
        addCards("Plains", 4, opponent);
        if (landDestructionDeck) {
            for (int i = 0; i < 4; i++) {
                addCardToZone("Sinkhole", ai, ZoneType.Library);
            }
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        return game;
    }

    @Test
    public void testStripMineInLandDestructionDeck() {
        Game game = stripGame(true);
        Player opponent = game.getPlayers().get(0);
        assertEquals(opponent.getLandsInPlay().size(), 3, "Strip Mine not used\n" + gameStateToString(game));
    }

    @Test
    public void testStripMineHeldOutsideLandDestructionDeck() {
        Game game = stripGame(false);
        Player opponent = game.getPlayers().get(0);
        assertEquals(opponent.getLandsInPlay().size(), 4, "Strip Mine used\n" + gameStateToString(game));
    }
}
