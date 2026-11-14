package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 83 (a tester report 29/10 15:37, Gwyll - Dragoonz turn 3): two lands, Nip Gwyllion in play,
// Nightsky Mimic and Edge of the Divinity in hand: the Mimic comes down, the aura waits for next turn's mana
public class BodyFirst83Test extends AITest {

    private Game setup(int lands) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        addCard("Plains", ai);
        for (int i = 1; i < lands; i++) {
            addCard("Swamp", ai);
        }
        addCard("Nip Gwyllion", ai);
        addCardToZone("Nightsky Mimic", ai, ZoneType.Hand);
        addCardToZone("Edge of the Divinity", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        ai.setLandsPlayedThisTurn(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        return game;
    }

    @Test
    public void testMimicBeforeAura() {
        Game game = setup(2);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Nightsky Mimic", ZoneType.Battlefield), 1, gameStateToString(game));
        assertEquals(countCardsWithName(game, "Edge of the Divinity", ZoneType.Battlefield), 0, gameStateToString(game));
    }

    @Test
    public void testBothWithEnoughMana() {
        Game game = setup(3);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Nightsky Mimic", ZoneType.Battlefield), 1, gameStateToString(game));
        assertEquals(countCardsWithName(game, "Edge of the Divinity", ZoneType.Battlefield), 1, gameStateToString(game));
    }
}
