package forge.ai;

import java.util.List;

import org.testng.annotations.Test;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: bourde watcher, mesure-101d / v12 (30/10): Bayou Groff counted castable for {1}{G} (59 mana blunders
// in v12) and Dusk Rose Reliquary for {W} (325), ignoring their AlternateAdditionalCost
public class BourdeVariantesTest extends AITest {

    private List<SpellAbility> variantes(final String name) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Card c = addCardToZone(name, ai, ZoneType.Hand);
        return BourdeWatcher.variantesCout(c);
    }

    @Test
    public void testBayouGroffPaysThree() {
        List<SpellAbility> v = variantes("Bayou Groff");
        assertEquals(v.size(), 1);
        assertEquals(v.get(0).getPayCosts().getTotalMana().getCMC(), 5);
        assertEquals(v.get(0).getPayCosts().isOnlyManaCost(), true);
    }

    @Test
    public void testDuskRoseNeedsSacrifice() {
        assertEquals(variantes("Dusk Rose Reliquary").size(), 0);
    }

    @Test
    public void testPlainSpellUnchanged() {
        List<SpellAbility> v = variantes("Grizzly Bears");
        assertEquals(v.size(), 1);
        assertEquals(v.get(0).getPayCosts().getTotalMana().getCMC(), 2);
    }
}
