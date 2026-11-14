package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 233, tournament v24: Crépitou holds two Curiosity next to Gelectrode (0/1, {T}: 1 damage) with four free lands
public class CuriosityPinger233Test extends AITest {

    private int cast(final String creature, final int lands) {
        return cast(creature, lands, PhaseType.MAIN2);
    }

    // rule 252: a plain creature only gets it before the attacks
    private int cast(final String creature, final int lands, final PhaseType phase) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        for (int i = 0; i < lands; i++) {
            addCard("Island", ai);
        }
        addCard(creature, ai).setSickness(false);
        addCardToZone("Curiosity", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        ai.setLandsPlayedThisTurn(1);
        game.getPhaseHandler().devModeSet(phase, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        return countCardsWithName(game, "Curiosity", ZoneType.Battlefield);
    }

    @Test
    public void testZeroPowerPingerGetsIt() {
        assertEquals(cast("Gelectrode", 3), 1);
    }

    @Test
    public void testZeroPowerWithoutPingStaysInHand() {
        assertEquals(cast("Fog Bank", 3), 0);
    }

    @Test
    public void testPlainCreatureStillGetsIt() {
        assertEquals(cast("Ludevic, Necro-Alchemist", 3, PhaseType.MAIN1), 1);
    }
}
