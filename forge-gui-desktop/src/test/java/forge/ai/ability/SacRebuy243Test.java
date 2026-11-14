package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

// magic-tools, règle 243 (tester 06/11, a test game, tour 12) : Woe Strider mange l'Experte que la Phalène
// ramène, elle rejoue sa défausse. Pas sans retour, pas sans main adverse, et l'ours n'est jamais mangé.
public class SacRebuy243Test extends AITest {

    private Card expert;
    private Card bears;

    private Game setup(String returner, int oppHand) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opp = game.getPlayers().get(0);
        addCard("Woe Strider", ai).setSickness(false);
        expert = addCard("Acquisitions Expert", ai);
        bears = addCard("Grizzly Bears", ai);
        if (returner != null) {
            addCard(returner, ai);
        }
        for (int i = 0; i < oppHand; i++) {
            addCardToZone("Grizzly Bears", opp, ZoneType.Hand);
        }
        fillLibrary(ai, 20);
        fillLibrary(opp, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        return game;
    }

    @Test(timeOut = 120000)
    public void testBroodmothRebuysTheDiscard() {
        Game game = setup("Luminous Broodmoth", 3);
        Player opp = game.getPlayers().get(0);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(opp.getCardsIn(ZoneType.Hand).size(), 2, gameStateToString(game));
        assertTrue(game.getPlayers().get(1).getCreaturesInPlay().anyMatch(c -> c.getName().equals("Acquisitions Expert")),
                gameStateToString(game));
        assertTrue(bears.isInPlay(), gameStateToString(game));
    }

    @Test(timeOut = 120000)
    public void testShepherdRebuysOnceOnly() {
        Game game = setup("Nightmare Shepherd", 3);
        Player opp = game.getPlayers().get(0);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(opp.getCardsIn(ZoneType.Hand).size(), 2, gameStateToString(game));
    }

    @Test(timeOut = 120000)
    public void testNoReturnKeepsTheCreature() {
        Game game = setup(null, 3);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertTrue(expert.isInPlay(), gameStateToString(game));
    }

    @Test(timeOut = 120000)
    public void testEmptyHandKeepsTheCreature() {
        Game game = setup("Luminous Broodmoth", 0);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertTrue(expert.isInPlay(), gameStateToString(game));
        assertFalse(game.getPlayers().get(1).getCardsIn(ZoneType.Graveyard).anyMatch(c -> true), gameStateToString(game));
    }
}
