package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: a tester report 29/10 11:41 (Auriok - Aragorn, turn 7): Vault Skirge and two Auriok Edgewright
// against an empty board, Sunspear Shikari still cast in main 2 into a possible sweeper
public class HoldBack93Test extends AITest {

    private Game setup(final int oppLands) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        for (int i = 0; i < 3; i++) {
            addCard("Plains", ai);
        }
        addCard("Vault Skirge", ai);
        addCard("Auriok Edgewright", ai);
        addCard("Auriok Edgewright", ai);
        addCardToZone("Sunspear Shikari", ai, ZoneType.Hand);
        for (int i = 0; i < oppLands; i++) {
            addCard("Plains", opponent);
        }
        addCardToZone("Wrath of God", opponent, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        ai.setLandsPlayedThisTurn(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        return game;
    }

    @Test
    public void testHoldBackIntoSweeper() {
        Game game = setup(4);
        assertEquals(countCardsWithName(game, "Sunspear Shikari", ZoneType.Hand), 1, gameStateToString(game));
    }

    @Test
    public void testCastWhenNoSweeperMana() {
        Game game = setup(2);
        assertEquals(countCardsWithName(game, "Sunspear Shikari", ZoneType.Battlefield), 1, gameStateToString(game));
    }
}
