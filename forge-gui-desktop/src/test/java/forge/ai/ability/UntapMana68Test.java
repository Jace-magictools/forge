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

public class UntapMana68Test extends AITest {

    // Règle 68 (tester, ElfBall) : Wirewood Symbiote dégage Elvish Archdruid (4 mana) plutôt que Llanowar Elves
    // (1 mana, même force sous l'Archidruide), assez pour Nettle Sentinel et Lead the Stampede
    @Test
    public void testSymbioteUntapsBiggestManaProducer() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");

        addCard("Wirewood Symbiote", ai);
        tappedNotSick(addCard("Llanowar Elves", ai));
        Card druid = tappedNotSick(addCard("Elvish Archdruid", ai));
        tappedNotSick(addCard("Fyndhorn Elves", ai));
        addCards("Elvish Warrior", 2, ai);
        addCardToZone("Nettle Sentinel", ai, ZoneType.Hand);
        addCardToZone("Lead the Stampede", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 30);
        ai.addLandPlayedThisTurn();

        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        gameLoopUntilNextPhase(game);

        assertEquals(countCardsWithName(game, "Lead the Stampede", ZoneType.Hand), 0);
        assertTrue(druid.isTapped());
    }

    private static Card tappedNotSick(Card c) {
        c.setSickness(false);
        c.setTapped(true);
        return c;
    }
}
