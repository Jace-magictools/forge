package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

// magic-tools: rule 124, Isochron Scepter with a spell that answers a spell (Reprieve) is used in response;
// with a bounce (Jilt), it is used during the opponent's turn (tournament v18, Patrilock: 0 activation)
public class Scepter124Test extends AITest {

    private Game scepterWith(String imprinted, boolean angelOnBattlefield) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        Card scepter = addCard("Isochron Scepter", ai);
        Card c = addCardToZone(imprinted, ai, ZoneType.Exile);
        c.setExiledWith(scepter);
        c.setExiledBy(ai);
        scepter.addExiledCard(c);
        scepter.addImprintedCard(c);
        addCard("Island", ai);
        addCard("Island", ai);
        for (int i = 0; i < 5; i++) {
            addCard("Plains", opponent);
        }
        if (angelOnBattlefield) {
            addCard("Serra Angel", opponent);
        } else {
            addCardToZone("Serra Angel", opponent, ZoneType.Hand);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, opponent);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.CLEANUP);
        return game;
    }

    @Test
    public void testReprieveInResponse() {
        Game game = scepterWith("Reprieve", false);
        assertEquals(countCardsWithName(game, "Serra Angel", ZoneType.Battlefield), 0, gameStateToString(game));
    }

    @Test
    public void testJiltBouncesOnTheirTurn() {
        Game game = scepterWith("Jilt", true);
        assertTrue(findCardWithName(game, "Isochron Scepter").isTapped(), "Scepter not used\n" + gameStateToString(game));
    }
}
