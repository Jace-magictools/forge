package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 237, relevé 6/11 (Infect): a second Rancor stays in hand on an enchanted creature with mana to spare;
// an aura that comes back to hand risks no two-for-one
public class StackAuraBack237Test extends AITest {

    private Game setup(String aura, String creature) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        addCard("Forest", ai);
        addCard("Swamp", ai);
        Card body = addCard(creature, ai);
        Card first = addCard("Rancor", ai);
        first.attachToEntity(body, null);
        addCardToZone(aura, ai, ZoneType.Hand);
        addCard("Grizzly Bears", opponent);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        ai.setLandsPlayedThisTurn(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        return game;
    }

    @Test
    public void testRancorStacksOnEnchantedCreature() {
        Game game = setup("Rancor", "Blight Mamba");
        playUntilPhase(game, PhaseType.COMBAT_BEGIN);
        assertEquals(countCardsWithName(game, "Rancor", ZoneType.Battlefield), 2, gameStateToString(game));
    }

    @Test
    public void testRancorStacksWithoutInfect() {
        Game game = setup("Rancor", "Hill Giant");
        playUntilPhase(game, PhaseType.COMBAT_BEGIN);
        assertEquals(countCardsWithName(game, "Rancor", ZoneType.Battlefield), 2, gameStateToString(game));
    }

    @Test
    public void testAuraThatStaysDeadIsKept() {
        Game game = setup("Unholy Strength", "Hill Giant");
        playUntilPhase(game, PhaseType.COMBAT_BEGIN);
        assertEquals(countCardsWithName(game, "Unholy Strength", ZoneType.Hand), 1, gameStateToString(game));
    }
}
