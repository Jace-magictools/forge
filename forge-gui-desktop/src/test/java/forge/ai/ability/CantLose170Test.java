package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 170, relevé « mana perdu » v207 (Eldratron - Avacyn) : à vie négative sous Platinum Angel, saSideEffects
// comparait 0 blessure d'arrivée à -7 vies et refusait tout permanent (BadEtbEffects)
public class CantLose170Test extends AITest {

    private Game setup(final boolean angel) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        for (int i = 0; i < 4; i++) {
            addCard("Island", ai);
        }
        if (angel) {
            addCard("Platinum Angel", ai);
        }
        addCardToZone("Darksteel Ingot", ai, ZoneType.Hand);
        addCard("Plains", opponent);
        ai.setLife(angel ? -7 : 20, null);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        ai.setLandsPlayedThisTurn(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        return game;
    }

    @Test
    public void testPermanentCastBelowZeroWhenCantLose() {
        Game game = setup(true);
        assertEquals(countCardsWithName(game, "Darksteel Ingot", ZoneType.Battlefield), 1, gameStateToString(game));
    }

    @Test
    public void testPermanentCastNormally() {
        Game game = setup(false);
        assertEquals(countCardsWithName(game, "Darksteel Ingot", ZoneType.Battlefield), 1, gameStateToString(game));
    }
}
