package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: Nazgul - Profanation (v22, turn 6): Swamp and two Plains, Dark Ritual, Entomb and Exhume in hand;
// the AI paid Entomb with its only black source and could not reanimate that turn (rule 139: ritual first)
public class Entomb139Test extends AITest {

    private Game setup(boolean withExhume) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        addCardToZone("Swamp", ai, ZoneType.Battlefield);
        addCardToZone("Plains", ai, ZoneType.Battlefield);
        addCardToZone("Plains", ai, ZoneType.Battlefield);
        addCardToZone("Dark Ritual", ai, ZoneType.Hand);
        addCardToZone("Entomb", ai, ZoneType.Hand);
        if (withExhume) {
            addCardToZone("Exhume", ai, ZoneType.Hand);
        }
        addCardToZone("Plains", opponent, ZoneType.Battlefield);
        fillLibrary(ai, 20);
        addCardToZone("Griselbrand", ai, ZoneType.Library);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        return game;
    }

    @Test
    public void testRitualThenEntombThenExhume() {
        Game game = setup(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Griselbrand", ZoneType.Battlefield), 1, gameStateToString(game));
    }

    @Test
    public void testRitualKeptWithoutReanimation() {
        Game game = setup(false);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Dark Ritual", ZoneType.Hand), 1, gameStateToString(game));
    }
}
