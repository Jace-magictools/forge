package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 230, Patrilock (6/11): the creature took the mana, the Scepter stayed in hand for turns
public class ImprintFirst230Test extends AITest {

    private Game setup(final String instant) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        for (int i = 0; i < 3; i++) {
            addCard("Island", ai);
        }
        addCardToZone("Isochron Scepter", ai, ZoneType.Hand);
        addCardToZone("Enigma Drake", ai, ZoneType.Hand);
        addCardToZone("Goblin Piker", ai, ZoneType.Hand);
        if (instant != null) {
            addCardToZone(instant, ai, ZoneType.Hand);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        ai.setLandsPlayedThisTurn(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.COMBAT_BEGIN);
        return game;
    }

    @Test
    public void testScepterFirstWithAnInstant() {
        Game game = setup("Counterspell");
        assertEquals(countCardsWithName(game, "Isochron Scepter", ZoneType.Battlefield), 1, gameStateToString(game));
        assertEquals(countCardsWithName(game, "Counterspell", ZoneType.Exile), 1, gameStateToString(game));
    }

    @Test
    public void testScepterWaitsWithoutInstant() {
        Game game = setup(null);
        assertEquals(countCardsWithName(game, "Isochron Scepter", ZoneType.Hand), 1, gameStateToString(game));
    }
}
