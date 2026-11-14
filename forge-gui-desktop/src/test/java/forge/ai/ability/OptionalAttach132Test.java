package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 132, a "you may" sub-ability (Optional$ True) does not veto the spell
// (tournament v19, Halvar: Resolute Strike 42 times in hand, never cast; AttachAi refuses non-trigger Attach subs)
public class OptionalAttach132Test extends AITest {

    private Game lethalPump(String spell) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCardToZone(spell, ai, ZoneType.Hand);
        addCard("Plains", ai);
        Card bear = addCard("Grizzly Bears", ai);
        bear.setSickness(false);
        opponent.setLife(4, null);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai, 5);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        return game;
    }

    @Test
    public void testResoluteStrikeCast() {
        Game game = lethalPump("Resolute Strike");
        assertEquals(countCardsWithName(game, "Resolute Strike", ZoneType.Graveyard), 1, gameStateToString(game));
    }
}
