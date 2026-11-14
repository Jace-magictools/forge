package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 75 (a tester report 29/10 12:59, Gwyll - Avacyn turn 9): with Ajani's Pridemate in play, Righteous
// Valkyrie goes before Serra Avenger (same cost): the Avenger's toughness becomes life and a counter
public class LifePayoff75Test extends AITest {

    private Game setup(boolean pridemate) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        for (int i = 0; i < 4; i++) {
            addCard("Plains", ai);
        }
        if (pridemate) {
            addCard("Ajani's Pridemate", ai);
        }
        addCardToZone("Serra Avenger", ai, ZoneType.Hand);
        addCardToZone("Righteous Valkyrie", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        ai.setLandsPlayedThisTurn(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        return game;
    }

    @Test
    public void testValkyrieFirstWithPridemate() {
        Game game = setup(true);
        Player ai = game.getPlayers().get(1);
        Card valk = null;
        for (Card c : ai.getCardsIn(ZoneType.Hand)) {
            if (c.getName().equals("Righteous Valkyrie")) {
                valk = c;
            }
        }
        assertEquals(forge.ai.EngineFirstAi.fedFromHand(ai, valk), true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Righteous Valkyrie", ZoneType.Battlefield), 1, gameStateToString(game));
    }

    @Test
    public void testValkyrieCountsItsOwnLife() {
        Game game = setup(false);
        Player ai = game.getPlayers().get(1);
        for (Card c : ai.getCardsIn(ZoneType.Hand)) {
            if (c.getName().equals("Righteous Valkyrie")) {
                assertEquals(forge.ai.EngineFirstAi.lifePayoff(ai, c), true);
            }
            if (c.getName().equals("Serra Avenger")) {
                assertEquals(forge.ai.EngineFirstAi.lifePayoff(ai, c), false);
            }
        }
    }

    @Test
    public void testNoPayoffOnEmptyBoard() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Card feast = addCardToZone("Case of the Uneaten Feast", ai, ZoneType.Hand);
        assertEquals(forge.ai.EngineFirstAi.lifePayoff(ai, feast), false);
    }
}
