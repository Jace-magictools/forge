package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 107 (a tester report 30/10 10:13, Ork - Profanation turn 1): Vampiric Tutor cast in our own second main
// phase instead of the opponent's end step
public class TutorTop107Test extends AITest {

    private Game setup(boolean aiTurn) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        addCardToZone("Swamp", ai, ZoneType.Battlefield);
        addCardToZone("Vampiric Tutor", ai, ZoneType.Hand);
        addCardToZone("Plains", ai, ZoneType.Hand);
        addCardToZone("Mountain", opponent, ZoneType.Battlefield);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, aiTurn ? ai : opponent);
        game.getAction().checkStateEffects(true);
        return game;
    }

    @Test
    public void testTutorWaitsInOwnTurn() {
        Game game = setup(true);
        playUntilPhase(game, PhaseType.CLEANUP);
        assertEquals(countCardsWithName(game, "Vampiric Tutor", ZoneType.Hand), 1, gameStateToString(game));
    }

    @Test
    public void testTutorAtOpponentEndStep() {
        Game game = setup(false);
        playUntilPhase(game, PhaseType.CLEANUP);
        assertEquals(countCardsWithName(game, "Vampiric Tutor", ZoneType.Graveyard), 1, gameStateToString(game));
    }
}
