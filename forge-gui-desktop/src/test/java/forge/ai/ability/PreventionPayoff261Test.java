package forge.ai.ability;

import java.util.ArrayList;
import java.util.List;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.ai.PlayerControllerAi;
import forge.ai.PreventionPayoff261;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.replacement.ReplacementEffect;
import forge.game.replacement.ReplacementType;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

// magic-tools rule 261 (D154, combos-forge 08/11, bench temp/d441): Vigor + Furnace of Rath + Walking Ballista, Vigor
// was applied alone (1 damage, 1 counter). The decider doubles the damage before Vigor or Purity turns it into counters
// or life. Control: a plain prevention (Circle of Protection style) keeps the stock order.
public class PreventionPayoff261Test extends AITest {

    private static ReplacementEffect damageRe(Card c) {
        for (ReplacementEffect re : c.getReplacementEffects()) {
            if (re.getMode() == ReplacementType.DamageDone) {
                return re;
            }
        }
        return null;
    }

    private static ReplacementEffect choose(Player ai, ReplacementEffect... res) {
        List<ReplacementEffect> list = new ArrayList<>(List.of(res));
        return ((PlayerControllerAi) ai.getController()).getAi().chooseSingleReplacementEffect(list);
    }

    @Test(timeOut = 120000)
    public void testFurnaceBeforeVigor() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ReplacementEffect vigor = damageRe(addCard("Vigor", ai));
        ReplacementEffect furnace = damageRe(addCard("Furnace of Rath", ai));
        addCard("Walking Ballista", ai);
        assertEquals(choose(ai, vigor, furnace), furnace, "Vigor listed first");
        assertEquals(choose(ai, furnace, vigor), furnace, "Furnace listed first");
    }

    @Test(timeOut = 120000)
    public void testDictateBeforePurity() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ReplacementEffect purity = damageRe(addCard("Purity", ai));
        ReplacementEffect dictate = damageRe(addCard("Dictate of the Twin Gods", ai));
        assertEquals(choose(ai, purity, dictate), dictate, "Purity listed first");
    }

    @Test(timeOut = 120000)
    public void testOpponentVigorNoPayoff() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        ReplacementEffect vigor = damageRe(addCard("Vigor", opponent));
        ReplacementEffect furnace = damageRe(addCard("Furnace of Rath", ai));
        assertNull(PreventionPayoff261.firstIncrease(ai, List.of(vigor, furnace)), "Vigor pays the opponent");
    }
}
