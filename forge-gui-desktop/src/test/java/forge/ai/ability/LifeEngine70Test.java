package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 70 (a tester report 29/10, ElfBall - Héliode turn 1): Héliode cast Case of the Uneaten Feast
// (1 life per creature) before Guide of Souls; a life-only trigger no longer jumps the line as an "engine" (rule 14)
public class LifeEngine70Test extends AITest {

    private Game setup(String... hand) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        addCard("Plains", ai);
        for (String c : hand) {
            addCardToZone(c, ai, ZoneType.Hand);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        ai.setLandsPlayedThisTurn(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        return game;
    }

    @Test
    public void testGuideBeforeLifeCase() {
        Game game = setup("Rally for the Throne", "Guide of Souls", "Cleric Class", "Plains", "Case of the Uneaten Feast");
        assertEquals(countCardsWithName(game, "Guide of Souls", ZoneType.Battlefield), 1, gameStateToString(game));
        assertEquals(countCardsWithName(game, "Case of the Uneaten Feast", ZoneType.Hand), 1, gameStateToString(game));
    }

    @Test
    public void testOnlyGainsLife() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        forge.game.card.Card feast = addCardToZone("Case of the Uneaten Feast", ai, ZoneType.Hand);
        forge.game.card.Card guide = addCardToZone("Guide of Souls", ai, ZoneType.Hand);
        forge.game.card.Card weapons = addCardToZone("Weapons Manufacturing", ai, ZoneType.Hand);
        assertEquals(forge.ai.EngineFirstAi.onlyGainsLife(feast, "TrigGainLife"), true);
        assertEquals(forge.ai.EngineFirstAi.onlyGainsLife(guide, "TrigGainLife"), false);
        assertEquals(forge.ai.EngineFirstAi.onlyGainsLife(weapons, "TrigToken"), false);
    }
}
