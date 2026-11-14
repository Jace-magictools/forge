package forge.ai;

import org.testng.annotations.Test;

import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

// magic-tools: rule 14, Weapons Manufacturing held while Kavaron spent its mana on other 2-drops
public class EngineFirstAiTest extends AITest {

    private Game game(String... hand) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        ((LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        addCard("Mountain", ai);
        addCard("Mountain", ai);
        for (String c : hand) {
            addCardToZone(c, ai, ZoneType.Hand);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        return game;
    }

    private static boolean inPlay(Player p, String name) {
        return p.getCardsIn(ZoneType.Battlefield).anyMatch(c -> c.getName().equals(name));
    }

    @Test
    public void testEngineBeforeOtherTwoDrops() {
        Game game = game("Ravenous Intruder", "Legion Extruder", "Weapons Manufacturing");
        Player ai = game.getPlayers().get(1);
        assertTrue(inPlay(ai, "Weapons Manufacturing"), "engine not cast first\n" + gameStateToString(game));
    }

    @Test
    public void testFedOnlyByMatchingCard() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        forge.game.card.Card engine = addCardToZone("Weapons Manufacturing", ai, ZoneType.Hand);
        addCardToZone("Ravenous Intruder", ai, ZoneType.Hand);
        addCardToZone("Mountain", ai, ZoneType.Hand);
        assertFalse(EngineFirstAi.fedFromHand(ai, engine), "fed by a nonartifact");
        addCardToZone("Legion Extruder", ai, ZoneType.Hand);
        assertTrue(EngineFirstAi.fedFromHand(ai, engine), "not fed by an artifact");
    }
}
