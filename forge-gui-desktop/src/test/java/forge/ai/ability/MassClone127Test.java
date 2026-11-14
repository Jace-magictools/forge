package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.CounterEnumType;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 127, mass clone of our own permanents (Mirrorform)
// (tournament v18, merfolk: 43 games with Mirrorform in hand, 0 cast)
public class MassClone127Test extends AITest {

    private Game mirrorformWith(String... small) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard("Serra Angel", ai).setSickness(false);
        for (String s : small) {
            addCard(s, ai).setSickness(false);
        }
        addCardToZone("Mirrorform", ai, ZoneType.Hand);
        for (int i = 0; i < 6; i++) {
            addCard("Island", ai);
        }
        addCard("Plains", opponent);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        return game;
    }

    @Test
    public void testCopiesTheAngel() {
        Game game = mirrorformWith("Grizzly Bears", "Llanowar Elves", "Memnite");
        assertEquals(countCardsWithName(game, "Serra Angel", ZoneType.Battlefield), 4, gameStateToString(game));
    }

    @Test
    public void testKeepsItForASingleCopy() {
        Game game = mirrorformWith("Grizzly Bears");
        assertEquals(countCardsWithName(game, "Mirrorform", ZoneType.Hand), 1, gameStateToString(game));
    }

    // rule 168: counters do not copy (v206, merfolk copied a 0/1 Plant made 5/6 by counters)
    @Test
    public void testIgnoresCountersOfTheTarget() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        for (String s : new String[] {"Centaur Courser", "Gray Ogre", "Hill Giant"}) {
            addCard(s, ai).setSickness(false);
        }
        addCardToZone("Mirrorform", ai, ZoneType.Hand);
        for (int i = 0; i < 6; i++) {
            addCard("Island", ai);
        }
        Card bear = addCard("Grizzly Bears", opponent);
        bear.addCounterInternal(CounterEnumType.P1P1, 6, opponent, false, null, null);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Mirrorform", ZoneType.Hand), 1, gameStateToString(game));
    }
}
