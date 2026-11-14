package forge.ai.sacrifice;

import forge.ai.simulation.SimulationTest;
import forge.ai.FodderAi;
import forge.game.Game;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.spellability.SpellAbility;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

// magic-tools: rule 81 (a tester report 29/10 17:12, Smeagol - carcasse): Altar of Dementia sacrificed the Plant token
// at Smeagol's own end step instead of keeping it to block, then sacrificing it on the opponent's turn
public class SacTiming81Test extends SimulationTest {

    private Game setup(Player[] out, int oppLibrary) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard("Altar of Dementia", ai);
        addToken("g_1_1_saproling", ai);
        addCard("Grizzly Bears", opponent);
        fillLibrary(ai, 20);
        fillLibrary(opponent, oppLibrary);
        out[0] = ai;
        out[1] = opponent;
        return game;
    }

    @Test
    public void testOwnTurnKeepsTheBlocker() {
        Player[] p = new Player[2];
        Game game = setup(p, 30);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, p[0]);
        game.getAction().checkStateEffects(true);
        gameLoopUntilNextPhase(game); // MAIN2 -> END_OF_TURN
        gameLoopUntilNextPhase(game); // END_OF_TURN -> CLEANUP
        assertEquals(countCardsWithName(game, "Saproling Token", ZoneType.Battlefield), 1, p[0].getCardsIn(ZoneType.Battlefield) + " / " + p[0].getCardsIn(ZoneType.Graveyard));
        assertEquals(p[1].getCardsIn(ZoneType.Library).size(), 30);
    }

    private Card fodderAt(Game game, Player ai, PhaseType phase, Player active) {
        game.getPhaseHandler().devModeSet(phase, active);
        game.getAction().checkStateEffects(true);
        Card altar = game.getCardState(ai.getCardsIn(ZoneType.Battlefield).stream()
                .filter(c -> c.getName().equals("Altar of Dementia")).findFirst().get());
        SpellAbility sa = altar.getSpellAbilities().stream().filter(x -> x.getApi() == ApiType.Mill).findFirst().get();
        sa.setActivatingPlayer(ai);
        return FodderAi.choose(ai, ai.getCreaturesInPlay(), sa);
    }

    @Test
    public void testFodderTiming() {
        Player[] p = new Player[2];
        Game game = setup(p, 30);
        assertNull(fodderAt(game, p[0], PhaseType.MAIN2, p[0]));
        assertNull(fodderAt(game, p[0], PhaseType.END_OF_TURN, p[0]));
        assertNull(fodderAt(game, p[0], PhaseType.COMBAT_BEGIN, p[1]));
        assertNotNull(fodderAt(game, p[0], PhaseType.END_OF_TURN, p[1]));
        assertNotNull(fodderAt(game, p[0], PhaseType.MAIN2, p[1]));
    }

    @Test
    public void testLethalMillDoesNotWait() {
        Player[] p = new Player[2];
        Game game = setup(p, 1);
        assertNotNull(fodderAt(game, p[0], PhaseType.MAIN2, p[0]));
    }
}
