package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 120, a triggered charm's mana mode is taken when it lets us cast a spell from hand now
public class ManaCharm120Test extends AITest {

    private Game run(int lands, String other) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        for (int i = 0; i < lands; i++) {
            addCard("Forest", ai);
        }
        addCardToZone("Culber and Stamets, Star-Crossed", ai, ZoneType.Hand);
        if (other != null) {
            addCardToZone(other, ai, ZoneType.Hand);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, opponent);
        game.getAction().checkStateEffects(true);
        playUntilNextTurn(game);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        return game;
    }

    // 4 Forests, Culber (4) + Grizzly Bears (2): Culber's two mana cast the Bears this turn
    @Test
    public void testManaModeCastsSecondSpell() {
        Game game = run(4, "Grizzly Bears");
        Player ai = game.getPlayers().get(1);
        assertEquals(countCardsWithName(game, "Culber and Stamets, Star-Crossed", ZoneType.Battlefield), 1, gameStateToString(game));
        assertEquals(countCardsWithName(game, "Grizzly Bears", ZoneType.Battlefield), 1, gameStateToString(game));
    }

    // 6 Forests: the drawn Runeclaw Bear is paid by the lands anyway, so the counter mode as before
    @Test
    public void testCounterModeWhenLandsSuffice() {
        Game game = run(6, null);
        assertEquals(countCardsWithName(game, "Culber and Stamets, Star-Crossed", ZoneType.Battlefield), 1, gameStateToString(game));
        Player ai = game.getPlayers().get(1);
        int counters = ai.getCardsIn(ZoneType.Battlefield).stream().mapToInt(c -> c.getCounters(forge.game.card.CounterEnumType.P1P1)).sum();
        assertEquals(counters, 1, gameStateToString(game));
    }
}
