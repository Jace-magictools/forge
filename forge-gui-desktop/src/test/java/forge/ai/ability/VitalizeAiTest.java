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

public class VitalizeAiTest extends AITest {

    // Vitalize untaps three tapped Llanowar Elves: with the Forest, enough to cast Vitalize then a 3-drop
    @Test
    public void testVitalizeUntapsElvesToCast() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);

        tappedNotSick(addCard("Llanowar Elves", ai));
        tappedNotSick(addCard("Llanowar Elves", ai));
        tappedNotSick(addCard("Llanowar Elves", ai));
        addCard("Forest", ai);
        addCardToZone("Vitalize", ai, ZoneType.Hand);
        addCardToZone("Centaur Courser", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 30);
        ai.addLandPlayedThisTurn();

        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        gameLoopUntilNextPhase(game);

        assertEquals(countCardsWithName(game, "Centaur Courser", ZoneType.Battlefield), 1);
        assertEquals(countCardsWithName(game, "Vitalize", ZoneType.Graveyard), 1);
    }

    // Nothing to cast with the extra mana: Vitalize stays in hand
    @Test
    public void testNoVitalizeWithoutSpellToCast() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);

        Card elf = tappedNotSick(addCard("Llanowar Elves", ai));
        tappedNotSick(addCard("Llanowar Elves", ai));
        addCard("Forest", ai);
        addCardToZone("Vitalize", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 30);
        ai.addLandPlayedThisTurn();

        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        gameLoopUntilNextPhase(game);

        assertEquals(countCardsWithName(game, "Vitalize", ZoneType.Hand), 1);
        assertTrue(elf.isTapped());
    }

    private static Card tappedNotSick(Card c) {
        c.setSickness(false);
        c.setTapped(true);
        return c;
    }
}
