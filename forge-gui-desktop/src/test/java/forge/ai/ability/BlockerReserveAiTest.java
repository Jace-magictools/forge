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

public class BlockerReserveAiTest extends AITest {

    // magic-tools: at 5 life against three Hill Giants, the two elves are the chump blocks we need: no Courser
    @Test
    public void testKeepsElvesUntappedWhenNextAttackIsLethal() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);

        ai.setLife(5, null);
        addCard("Forest", ai);
        Card elf1 = addCard("Llanowar Elves", ai);
        elf1.setSickness(false);
        Card elf2 = addCard("Llanowar Elves", ai);
        elf2.setSickness(false);
        addCardToZone("Centaur Courser", ai, ZoneType.Hand);
        addCards("Hill Giant", 3, opponent);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 30);

        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        gameLoopUntilNextPhase(game);

        assertEquals(countCardsWithName(game, "Centaur Courser", ZoneType.Battlefield), 0);
        assertFalse(elf1.isTapped());
        assertFalse(elf2.isTapped());
    }

    // Same board at 20 life: no danger, the elves pay for the Courser
    @Test
    public void testTapsElvesWhenSafe() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);

        addCard("Forest", ai);
        addCards("Llanowar Elves", 2, ai).forEach(c -> c.setSickness(false));
        addCardToZone("Centaur Courser", ai, ZoneType.Hand);
        addCards("Hill Giant", 3, opponent);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 30);

        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        gameLoopUntilNextPhase(game);

        assertEquals(countCardsWithName(game, "Centaur Courser", ZoneType.Battlefield), 1);
    }

    // Heritage Druid: tapping three elves would leave too few blockers
    @Test
    public void testHeritageDruidKeepsBlockers() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);

        ai.setLife(5, null);
        addCard("Heritage Druid", ai).setSickness(true);
        addCards("Elvish Warrior", 2, ai).forEach(c -> c.setSickness(true));
        addCardToZone("Centaur Courser", ai, ZoneType.Hand);
        addCards("Hill Giant", 3, opponent);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 30);

        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        gameLoopUntilNextPhase(game);

        assertEquals(countCardsWithName(game, "Centaur Courser", ZoneType.Battlefield), 0);
    }
    // Both sides pump their creatures with mana from a creature (Nantuko Shade + Elves of Deep Shadow): pricing the
    // pump of an attacker asked the reserve again, without end (StackOverflowError, Verdu against elfe, tournament v6)
    @Test
    public void testNoRecursionWhenAttackersPumpWithCreatureMana() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);

        ai.setLife(5, null);
        for (Player p : game.getPlayers()) {
            addCard("Nantuko Shade", p).setSickness(false);
            addCard("Elves of Deep Shadow", p).setSickness(false);
        }
        addCard("Forest", ai);
        addCards("Llanowar Elves", 2, ai).forEach(c -> c.setSickness(false));
        addCardToZone("Centaur Courser", ai, ZoneType.Hand);
        addCards("Hill Giant", 3, opponent);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 30);

        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        gameLoopUntilNextPhase(game);

        assertEquals(countCardsWithName(game, "Centaur Courser", ZoneType.Battlefield), 0);
    }
}
