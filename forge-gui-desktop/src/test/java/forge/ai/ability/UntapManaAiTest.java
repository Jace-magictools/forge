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

public class UntapManaAiTest extends AITest {

    // Wirewood Symbiote returns a tapped Elf to untap Elvish Archdruid, enough to cast a 3-drop
    @Test
    public void testSymbioteUntapsArchdruidToCast() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);

        addCard("Wirewood Symbiote", ai);
        tappedNotSick(addCard("Elvish Archdruid", ai));
        tappedNotSick(addCard("Llanowar Elves", ai));
        addCards("Elvish Warrior", 2, ai);
        addCardToZone("Centaur Courser", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 30);

        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        gameLoopUntilNextPhase(game);

        assertEquals(countCardsWithName(game, "Centaur Courser", ZoneType.Battlefield), 1);
        assertEquals(countCardsWithName(game, "Llanowar Elves", ZoneType.Hand), 1);
    }

    // Quirion Ranger returns the tapped Forest, not an untapped one
    @Test
    public void testQuirionRangerUntapsArchdruidToCast() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);

        addCard("Quirion Ranger", ai);
        tappedNotSick(addCard("Elvish Archdruid", ai));
        addCard("Elvish Warrior", ai);
        addCard("Forest", ai).setTapped(true);
        addCardToZone("Rumbling Baloth", ai, ZoneType.Hand);
        addCard("Island", ai);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 30);
        ai.addLandPlayedThisTurn();

        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        gameLoopUntilNextPhase(game);

        // {2}{G}{G}: GGG from the Archdruid (three Elves) + the Island
        assertEquals(countCardsWithName(game, "Rumbling Baloth", ZoneType.Battlefield), 1);
        assertEquals(countCardsWithName(game, "Forest", ZoneType.Hand), 1);
        assertEquals(countCardsWithName(game, "Island", ZoneType.Battlefield), 1);
    }

    // Nothing to cast: the untap ability is not used, the Forest stays in play
    @Test
    public void testNoUntapWithoutSpellToCast() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);

        addCard("Quirion Ranger", ai);
        Card druid = tappedNotSick(addCard("Elvish Archdruid", ai));
        addCard("Forest", ai).setTapped(true);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 30);

        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        gameLoopUntilNextPhase(game);

        assertEquals(countCardsWithName(game, "Forest", ZoneType.Battlefield), 1);
        assertTrue(druid.isTapped());
    }

    private static Card tappedNotSick(Card c) {
        c.setSickness(false);
        c.setTapped(true);
        return c;
    }
}
