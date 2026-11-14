package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 76 (a tester report 29/10 13:11, Gwyll): with two white-black hybrids in play, Deathbringer Liege
// (+2/+2 to each) goes before Obzedat, Ghost Council (same cost, bigger body)
public class LordFirst76Test extends AITest {

    private Game setup(boolean hybrids) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        for (int i = 0; i < 2; i++) {
            addCard("Plains", ai);
        }
        for (int i = 0; i < 3; i++) {
            addCard("Swamp", ai);
        }
        if (hybrids) {
            addCard("Nip Gwyllion", ai);
            addCard("Nip Gwyllion", ai);
        }
        addCardToZone("Obzedat, Ghost Council", ai, ZoneType.Hand);
        addCardToZone("Deathbringer Liege", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        ai.setLandsPlayedThisTurn(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        return game;
    }

    private Card inHand(Player ai, String name) {
        for (Card c : ai.getCardsIn(ZoneType.Hand)) {
            if (c.getName().equals(name)) {
                return c;
            }
        }
        return null;
    }

    @Test
    public void testLiegeFirstWithHybrids() {
        Game game = setup(true);
        Player ai = game.getPlayers().get(1);
        // two hybrids, each +1/+1 twice: 2 x 2 x (15 + 10)
        assertEquals(forge.ai.LordFirstAi.boardBonus(ai, inHand(ai, "Deathbringer Liege")), 100);
        assertEquals(forge.ai.LordFirstAi.boardBonus(ai, inHand(ai, "Obzedat, Ghost Council")), 0);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Deathbringer Liege", ZoneType.Battlefield), 1, gameStateToString(game));
    }

    @Test
    public void testNoBonusOnEmptyBoard() {
        Game game = setup(false);
        Player ai = game.getPlayers().get(1);
        assertEquals(forge.ai.LordFirstAi.boardBonus(ai, inHand(ai, "Deathbringer Liege")), 0);
    }
}
