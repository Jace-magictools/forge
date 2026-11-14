package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

// magic-tools: rule 167 (relevé « mana perdu », 04/11, Eldratron): our own Culling Scales targets our Expedition Map
// or Sensei's Divining Top at our upkeep; the AI answers its own trigger and uses the permanent before it goes
// (Map searches a land, Top draws and goes back on top of the library) instead of letting it be destroyed.
public class OwnThreat167Test extends AITest {

    private Game setup(String perm) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        for (int i = 0; i < 3; i++) {
            addCard("Island", ai);
        }
        addCard("Culling Scales", ai);
        Card c = addCard(perm, ai);
        c.setSickness(false);
        addCard("Grizzly Bears", opponent);
        fillLibrary(ai, 20);
        addCardToZone("Island", ai, ZoneType.Library);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.UNTAP, ai);
        game.getAction().checkStateEffects(true);
        return game;
    }

    @Test
    public void testMapUsedInResponse() {
        Game game = setup("Expedition Map");
        Player ai = game.getPlayers().get(1);
        playUntilPhase(game, PhaseType.DRAW);
        assertEquals(countCardsWithName(game, "Expedition Map", ZoneType.Battlefield), 0, gameStateToString(game));
        // the Map searched the only land of the library before being destroyed
        assertTrue(ai.getCardsIn(ZoneType.Hand).anyMatch(Card::isLand), gameStateToString(game));
    }

    @Test
    public void testTopUsedInResponse() {
        Game game = setup("Sensei's Divining Top");
        playUntilPhase(game, PhaseType.DRAW);
        assertEquals(countCardsWithName(game, "Sensei's Divining Top", ZoneType.Graveyard), 0, gameStateToString(game));
    }
}
