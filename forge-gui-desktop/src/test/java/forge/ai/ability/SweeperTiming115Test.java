package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 115 (a tester report 30/10 18:20, Schizoph - Profanation turn 8): Drown in Sorrow waits for main 2
// so that Akroma, Angel of Wrath attacks as a 6/6 (the Grizzly Bears it kills could not block a flyer anyway)
public class SweeperTiming115Test extends AITest {

    private Game setup(String oppCreature) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        opponent.setLife(20, null);
        for (int i = 0; i < 3; i++) {
            addCard("Swamp", ai);
        }
        Card akroma = addCard("Akroma, Angel of Wrath", ai);
        akroma.setSickness(false);
        for (int i = 0; i < 3; i++) {
            addCard(oppCreature, opponent);
        }
        addCardToZone("Drown in Sorrow", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        return game;
    }

    @Test
    public void testAttacksBeforeSweeper() {
        Game game = setup("Grizzly Bears");
        playUntilPhase(game, PhaseType.END_OF_TURN);
        Player opponent = game.getPlayers().get(0);
        assertEquals(countCardsWithName(game, "Grizzly Bears", ZoneType.Battlefield), 0, gameStateToString(game));
        assertEquals(opponent.getLife(), 14, gameStateToString(game));
    }

    @Test
    public void testSweepsFirstWhenVictimsCouldBlock() {
        // Suntail Hawk flies: it could block Akroma, so the sweeper is cast before combat as before
        Game game = setup("Suntail Hawk");
        playUntilPhase(game, PhaseType.COMBAT_DECLARE_ATTACKERS);
        assertEquals(countCardsWithName(game, "Suntail Hawk", ZoneType.Battlefield), 0, gameStateToString(game));
    }
}
