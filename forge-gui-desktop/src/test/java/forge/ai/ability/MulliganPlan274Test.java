package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.ai.ComputerUtil;
import forge.ai.PlayerControllerAi;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.card.CardCollection;
import forge.game.card.CardCollectionView;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

// magic-tools rule 274 (tester 10/11 08:26, « le plan du deck à chaque tour » : mulligan). A combo deck keeps the
// key cards when the London mulligan puts cards back. (The 10/11 first version also sent back seven-card hands with
// nothing of the plan : targeted duel 48.5 %, z -1.67, withdrawn.)
public class MulliganPlan274Test extends AITest {

    private Player setUp(boolean plan) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        if (plan) {
            Deck d = ai.getRegisteredPlayer().getDeck();
            d.addKeyCard("Viscera Seer");
            d.addKeyCard("Kitchen Finks");
            d.addKeyCard("Melira, Sylvok Outcast");
            d.setAiHints("Combo$Melira, Sylvok Outcast;Kitchen Finks;Viscera Seer | Route$reach");
        }
        for (int i = 0; i < 12; i++) {
            addCardToZone("Forest", ai, ZoneType.Library);
        }
        fillLibrary(ai, 20);
        fillLibrary(game.getPlayers().get(0), 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        return ai;
    }

    private void hand(Player ai, String... names) {
        for (String n : names) {
            addCardToZone(n, ai, ZoneType.Hand);
        }
    }

    private String tucked(String key) {
        Player ai = setUp(false);
        if (key != null) {
            ai.getRegisteredPlayer().getDeck().addKeyCard(key);
        }
        hand(ai, "Forest", "Forest", "Plains", "Grizzly Bears", "Silvercoat Lion", "Llanowar Elves", "Melira, Sylvok Outcast");
        CardCollectionView back = ((PlayerControllerAi) ai.getController())
                .tuckCardsViaMulligan(new CardCollection(ai.getCardsIn(ZoneType.Hand)), 1);
        return back.getFirst().getName();
    }

    @Test(timeOut = 120000)
    public void testTuckKeepsKey() {
        String control = tucked(null);
        assertFalse(control.equals(tucked(control)), "the plan keeps " + control + ", put back without it");
    }
}
