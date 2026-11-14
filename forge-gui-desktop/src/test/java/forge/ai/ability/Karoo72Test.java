package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 72 (a tester report 29/10 11:59, Troll - ElfBall turn 5): Troll played Golgari Rot Farm first,
// bounced an untapped land and could no longer cast Varolz; take the mana of the lands first, then play the karoo
public class Karoo72Test extends AITest {

    private Game setup(boolean forestTapped, String... hand) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        addCardToZone("Swamp", ai, ZoneType.Battlefield);
        Card forest = addCardToZone("Forest", ai, ZoneType.Battlefield);
        if (forestTapped) {
            forest.setTapped(true);
        }
        for (String c : hand) {
            addCardToZone(c, ai, ZoneType.Hand);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        return game;
    }

    @Test
    public void testTwoDropThenKaroo() {
        Game game = setup(false, "Golgari Rot Farm", "Ghoulcaller's Accomplice");
        assertEquals(countCardsWithName(game, "Golgari Rot Farm", ZoneType.Battlefield), 1, gameStateToString(game));
        assertEquals(countCardsWithName(game, "Ghoulcaller's Accomplice", ZoneType.Battlefield), 1, gameStateToString(game));
    }

    @Test
    public void testReturnsTheTappedLand() {
        Game game = setup(true, "Golgari Rot Farm");
        assertEquals(countCardsWithName(game, "Forest", ZoneType.Hand), 1, gameStateToString(game));
        assertEquals(countCardsWithName(game, "Swamp", ZoneType.Battlefield), 1, gameStateToString(game));
    }
}
