package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

public class ElfManaAiTest extends AITest {

    // Heritage Druid is no longer removed from AI decks: the AI taps three Elves (even summoning sick) for GGG
    @Test
    public void testHeritageDruidPaysWithThreeElves() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);

        addCard("Heritage Druid", ai).setSickness(true);
        addCards("Elvish Warrior", 2, ai).forEach(c -> c.setSickness(true));
        addCardToZone("Centaur Courser", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 30);

        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        gameLoopUntilNextPhase(game);

        assertEquals(countCardsWithName(game, "Centaur Courser", ZoneType.Battlefield), 1);
    }

    // Birchlore Rangers: two Elves make one mana of any color
    @Test
    public void testBirchloreRangersMakesAnyColor() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);

        addCard("Birchlore Rangers", ai);
        addCard("Elvish Warrior", ai);
        addCardToZone("Merfolk of the Pearl Trident", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 30);

        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        gameLoopUntilNextPhase(game);

        assertEquals(countCardsWithName(game, "Merfolk of the Pearl Trident", ZoneType.Battlefield), 1);
    }

    // The AI casts Heritage Druid itself
    @Test
    public void testAiCastsHeritageDruid() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);

        addCard("Forest", ai);
        addCardToZone("Heritage Druid", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 30);

        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        gameLoopUntilNextPhase(game);

        assertEquals(countCardsWithName(game, "Heritage Druid", ZoneType.Battlefield), 1);
    }
}
