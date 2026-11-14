package forge.ai.simulation;

import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.CounterEnumType;
import forge.game.card.CounterType;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

/** Demande D126 (combos-forge, 5/11) : la copie de recherche d'une partie avec des marqueurs rad ne plante plus et n'a qu'un effet Radiation. */
public class RadiationCopyTest extends SimulationTest {
    @Test
    public void testCopyWithRadCounters() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        addCard("Mountain", p);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, p);
        Multiset<CounterType> rad = HashMultiset.create();
        rad.add(CounterEnumType.RAD, 3);
        p.setCounters(rad);
        game.getAction().checkStateEffects(true);

        GameCopier copier = new GameCopier(game);
        Game copy = copier.makeCopy();
        Player q = copy.getPlayers().get(1);
        AssertJUnit.assertEquals(3, q.getCounters(CounterEnumType.RAD));
        int effects = 0;
        for (Card c : q.getCardsIn(ZoneType.Command)) {
            if ("Radiation".equals(c.getName())) {
                effects++;
                AssertJUnit.assertNotNull(copier.find(p.getCardsIn(ZoneType.Command).getFirst()));
            }
        }
        AssertJUnit.assertEquals(1, effects);
    }
}
