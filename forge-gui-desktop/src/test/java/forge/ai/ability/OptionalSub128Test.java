package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 128, an optional sub-ability ("up to one target") the AI does not like does not veto the spell
// (tournament v18, Tymaret: Twisted Fealty 34 times in hand, never cast, while Mark of Mutiny was)
public class OptionalSub128Test extends AITest {

    private Game steal(String spell) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCardToZone(spell, ai, ZoneType.Hand);
        for (int i = 0; i < 3; i++) {
            addCard("Mountain", ai);
        }
        addCard("Serra Angel", opponent);
        opponent.setLife(4, null);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        return game;
    }

    @Test
    public void testMarkOfMutinyControl() {
        Game game = steal("Mark of Mutiny");
        assertEquals(countCardsWithName(game, "Mark of Mutiny", ZoneType.Graveyard), 1, gameStateToString(game));
    }

    @Test
    public void testTwistedFealtyCast() {
        Game game = steal("Twisted Fealty");
        assertEquals(countCardsWithName(game, "Twisted Fealty", ZoneType.Graveyard), 1, gameStateToString(game));
    }
}
