package forge.ai;

import org.testng.annotations.Test;

import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class HarmlessEtbAiTest extends AITest {

    // Sygg, Wanderwine Wisdom: its ETB grants "draw on combat damage" until end of turn, useless in main 2,
    // that is no reason to keep an unblockable 2/2 for 2 in hand (BadEtbEffects)
    @Test
    public void testSyggCastInMain2() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);

        addCard("Island", ai);
        addCard("Island", ai);
        addCardToZone("Sygg, Wanderwine Wisdom", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 30);

        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        gameLoopUntilNextPhase(game);

        assertEquals(ai.getCreaturesInPlay().size(), 1, "Sygg kept in hand");
    }

    // Champions of the Shoal: "tap up to one target creature" may target nothing
    @Test
    public void testChampionsCastWithoutTarget() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);

        for (int i = 0; i < 4; i++) {
            addCard("Island", ai);
        }
        addCardToZone("Champions of the Shoal", ai, ZoneType.Hand);
        addCard("Merfolk of the Pearl Trident", ai);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 30);

        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        gameLoopUntilNextPhase(game);

        assertTrue(ai.getCreaturesInPlay().stream().anyMatch(c -> c.getName().equals("Champions of the Shoal")),
                "Champions of the Shoal kept in hand");
    }

    // Snapcaster Mage: nothing in the graveyard worth replaying (Lightning Bolt, no red mana), the trigger
    // gives flashback to our own card and cannot hurt us; the 2/1 flash must not stay in hand forever
    @Test
    public void testSnapcasterCastWithoutReplay() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);

        addCard("Island", ai);
        addCard("Island", ai);
        addCardToZone("Snapcaster Mage", ai, ZoneType.Hand);
        addCardToZone("Lightning Bolt", ai, ZoneType.Graveyard);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 30);

        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        for (int i = 0; i < 12 && ai.getCreaturesInPlay().isEmpty(); i++) {
            gameLoopUntilNextPhase(game);
        }

        assertEquals(ai.getCreaturesInPlay().size(), 1, "Snapcaster Mage kept in hand");
    }

    // Long River Lurker: "target creature you control can't be blocked this turn" (an Effect on our own
    // creature, it can target itself) is no reason to keep a 2/3 ward 1 for 3 in hand
    @Test
    public void testLongRiverLurkerCastInMain2() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);

        for (int i = 0; i < 3; i++) {
            addCard("Island", ai);
        }
        addCardToZone("Long River Lurker", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 30);

        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        gameLoopUntilNextPhase(game);

        assertEquals(ai.getCreaturesInPlay().size(), 1, "Long River Lurker kept in hand");
    }

    // Fanatic of Xenagos: Tribute 1, "if tribute wasn't paid, it gets +1/+1 and gains haste" pumps itself only,
    // that is no reason to keep a 3/3 trample for 3 in hand (BadEtbEffects in every main phase)
    @Test
    public void testTributeSelfPumpCast() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);

        addCard("Mountain", ai);
        addCard("Forest", ai);
        addCard("Forest", ai);
        addCardToZone("Fanatic of Xenagos", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 30);

        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        gameLoopUntilNextPhase(game);

        assertEquals(ai.getCreaturesInPlay().size(), 1, "Fanatic of Xenagos kept in hand");
    }

    // rule 32: Exemplar of Strength alone (4/4, three -1/-1 counters on target creature you control) can
    // target itself and stays a 1/1; Forge kept it in hand as BadEtbEffects (39 turns, Hapatra vs Elemental)
    @Test
    public void testExemplarCastAlone() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);

        addCard("Forest", ai);
        addCard("Forest", ai);
        addCardToZone("Exemplar of Strength", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 30);

        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        gameLoopUntilNextPhase(game);

        assertEquals(ai.getCreaturesInPlay().size(), 1, "Exemplar of Strength kept in hand");
    }

    // rule 32: with a Llanowar Elves in play, the counters go on the Exemplar itself, not on the 1/1 they kill
    @Test
    public void testExemplarSparesSmallCreature() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);

        addCard("Forest", ai);
        addCard("Forest", ai);
        addCard("Llanowar Elves", ai);
        addCardToZone("Exemplar of Strength", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 30);

        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        gameLoopUntilNextPhase(game);

        assertEquals(ai.getCreaturesInPlay().size(), 2, "Exemplar kept in hand or Llanowar Elves killed");
    }

    // rule 32: Channeler Initiate (3/4, three -1/-1 counters) alone survives as a 0/1 mana creature
    @Test
    public void testChannelerCastAlone() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);

        addCard("Forest", ai);
        addCard("Forest", ai);
        addCardToZone("Channeler Initiate", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 30);

        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        gameLoopUntilNextPhase(game);

        assertEquals(ai.getCreaturesInPlay().size(), 1, "Channeler Initiate kept in hand");
    }
}
