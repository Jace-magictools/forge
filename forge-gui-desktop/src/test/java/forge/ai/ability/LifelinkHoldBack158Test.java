package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 158, banc a test game (human cast a second Nip Gwyllion at 11 life against 17, three creatures
// against an empty board): rule 93 kept the lifelink body in hand for fear of a sweeper
public class LifelinkHoldBack158Test extends AITest {

    private Game setup(final int aiLife) {
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
        addCardToZone("Nip Gwyllion", ai, ZoneType.Hand);
        for (int i = 0; i < 4; i++) {
            addCard("Plains", opponent);
        }
        addCardToZone("Wrath of God", opponent, ZoneType.Hand);
        ai.setLife(aiLife, null);
        opponent.setLife(17, null);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        ai.setLandsPlayedThisTurn(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        return game;
    }

    @Test
    public void testLifelinkCastWhenBehindOnLife() {
        Game game = setup(11);
        assertEquals(countCardsWithName(game, "Nip Gwyllion", ZoneType.Battlefield), 1, gameStateToString(game));
    }

    @Test
    public void testLifelinkHeldWhenAhead() {
        Game game = setup(20);
        assertEquals(countCardsWithName(game, "Nip Gwyllion", ZoneType.Hand), 1, gameStateToString(game));
    }
}
