package forge.ai;

import org.testng.annotations.Test;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.combat.Combat;
import forge.game.keyword.Keyword;
import forge.game.player.Player;

import static org.testng.Assert.assertEquals;

// magic-tools: bourde watcher, v14 (30/10): blocks against Rafiq of the Many (double strike when attacking alone)
// were counted as blunders, the unblocked damage ignored double strike (10 life, "5 damage", actually 10)
public class BourdeDoubleStrikeTest extends AITest {

    @Test
    public void testDoubleStrikeCountsTwice() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opp = game.getPlayers().get(0);
        Card raptor = addCard("Ridgetop Raptor", opp);
        Combat combat = new Combat(opp);
        combat.addAttacker(raptor, ai);
        assertEquals(raptor.hasKeyword(Keyword.DOUBLE_STRIKE), true);
        assertEquals(BourdeWatcher.degatsSansBloquer(ai, combat), 4);
    }

    @Test
    public void testPlainAttacker() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opp = game.getPlayers().get(0);
        Card bears = addCard("Grizzly Bears", opp);
        Combat combat = new Combat(opp);
        combat.addAttacker(bears, ai);
        assertEquals(BourdeWatcher.degatsSansBloquer(ai, combat), 2);
    }
}
