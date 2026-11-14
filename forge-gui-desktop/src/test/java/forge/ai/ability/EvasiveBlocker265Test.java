package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 265 (tester 09/11, a test game turn 7): at 6 life against a 7/2 flyer, Maned Serval
// was cast and Inspiring Overseer (2/1 flying) kept in hand.
public class EvasiveBlocker265Test extends AITest {

    private Game setup(int life, String oppFlyer, PhaseType phase) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        for (int i = 0; i < 3; i++) {
            addCard("Plains", ai);
        }
        Card b = addCard("Spirited Companion", ai);
        b.setSickness(false);
        Card f = addCard(oppFlyer, opponent);
        f.setSickness(false);
        addCardToZone("Maned Serval", ai, ZoneType.Hand);
        addCardToZone("Inspiring Overseer", ai, ZoneType.Hand);
        addCardToZone("Swamp", opponent, ZoneType.Library);
        ai.setLife(life, null);
        opponent.setLife(30, null);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        ai.setLandsPlayedThisTurn(1);
        game.getPhaseHandler().devModeSet(phase, ai, 7);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        return game;
    }

    /** 4 life, a 4/4 flyer in front: the flyer blocker comes down */
    @Test
    public void testFlyerBlocker() {
        Game game = setup(4, "Serra Angel", PhaseType.MAIN2);
        assertEquals(countCardsWithName(game, "Inspiring Overseer", ZoneType.Battlefield), 1, gameStateToString(game));
    }

    /** same from main 1 */
    @Test
    public void testFlyerBlockerMain1() {
        Game game = setup(4, "Serra Angel", PhaseType.MAIN1);
        assertEquals(countCardsWithName(game, "Inspiring Overseer", ZoneType.Battlefield), 1, gameStateToString(game));
    }
}
