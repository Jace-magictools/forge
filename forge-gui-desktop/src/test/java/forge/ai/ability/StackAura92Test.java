package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: a tester report 29/10 12:20 (Dauthi - ElfBall, turn 5): Vault Skirge already wearing Ordeal of Erebos,
// Unholy Strength in hand, ElfBall with ground elves only; Forge never stacks a second aura on an enchanted creature
public class StackAura92Test extends AITest {

    @Test
    public void testAuraOnEnchantedFlyer() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        addCard("Swamp", ai);
        forge.game.card.Card skirge = addCard("Vault Skirge", ai);
        forge.game.card.Card ordeal = addCard("Ordeal of Erebos", ai);
        ordeal.attachToEntity(skirge, null);
        addCardToZone("Unholy Strength", ai, ZoneType.Hand);
        addCard("Llanowar Elves", opponent);
        addCard("Elvish Mystic", opponent);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        ai.setLandsPlayedThisTurn(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Unholy Strength", ZoneType.Battlefield), 1, gameStateToString(game));
    }

    @Test
    public void testAuraKeptAgainstFlyingBlocker() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        addCard("Swamp", ai);
        forge.game.card.Card skirge = addCard("Vault Skirge", ai);
        forge.game.card.Card ordeal = addCard("Ordeal of Erebos", ai);
        ordeal.attachToEntity(skirge, null);
        addCardToZone("Unholy Strength", ai, ZoneType.Hand);
        addCard("Wind Drake", opponent);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        ai.setLandsPlayedThisTurn(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Unholy Strength", ZoneType.Hand), 1, gameStateToString(game));
    }
}
