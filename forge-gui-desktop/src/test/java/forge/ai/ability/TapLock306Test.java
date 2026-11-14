package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertTrue;

// magic-tools: rule 306 (D174, tester 12/11): a "tap target creature, it doesn't untap" (Dungeon Geists, Frost Lynx)
// locks the creature that weighs most (Watchwolf 3/3), not the most expensive one (Bottle Gnomes 1/3, three mana).
public class TapLock306Test extends AITest {

    private void run(final String locker) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCards("Island", 4, ai);
        addCardToZone(locker, ai, ZoneType.Hand);
        Card gnomes = addCard("Bottle Gnomes", opponent);
        Card wolf = addCard("Watchwolf", opponent);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 30);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        gameLoopUntilNextPhase(game);
        assertTrue(countCardsWithName(game, locker, ZoneType.Battlefield) == 1, gameStateToString(game));
        assertTrue(wolf.isTapped() && !gnomes.isTapped(), gameStateToString(game));
    }

    @Test
    public void testDungeonGeistsLocksTheBiggest() {
        run("Dungeon Geists");
    }

    @Test
    public void testFrostLynxLocksTheBiggest() {
        run("Frost Lynx");
    }
}
