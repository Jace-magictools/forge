package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/** Rule 94: a tutor fetches the reanimation spell when the fatty is still in hand (tester 29/10, Profanation). */
public class ReanimatorPlan94Test extends AITest {

    private Game setUp(String... hand) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard("Swamp", ai);
        addCard("Swamp", ai);
        addCardToZone("Demonic Tutor", ai, ZoneType.Hand);
        for (String h : hand) {
            addCardToZone(h, ai, ZoneType.Hand);
        }
        for (String l : new String[] {"Sparkhunter Masticore", "Animate Dead", "Reanimate", "Dark Ritual", "Sol Ring", "Wrath of God"}) {
            addCardToZone(l, ai, ZoneType.Library);
        }
        for (int i = 0; i < 8; i++) {
            addCardToZone("Swamp", ai, ZoneType.Library);
            addCardToZone("Plains", ai, ZoneType.Library);
        }
        fillLibrary(opponent, 20);
        moveToMain2(game, ai);
        return game;
    }

    private static boolean holdsReanimation(Player ai) {
        return ai.getCardsIn(ZoneType.Hand).anyMatch(c -> c.getName().equals("Animate Dead") || c.getName().equals("Reanimate"));
    }

    private static String zones(Player ai) {
        return "graveyard=" + ai.getCardsIn(ZoneType.Graveyard) + " hand=" + ai.getCardsIn(ZoneType.Hand);
    }

    @Test
    public void testTutorFindsReanimationWhenFattyInHand() {
        Game game = setUp("Iona, Shield of Emeria", "Sickening Dreams", "Plains");
        Player ai = game.getPlayers().get(1);
        gameLoopUntilNextPhase(game);
        assertTrue(holdsReanimation(ai), zones(ai));
    }

    @Test
    public void testNoFattyKeepsUsualChoice() {
        // witness: no fatty anywhere, the reanimation spell is not the missing piece
        Game game = setUp("Grizzly Bears", "Plains");
        Player ai = game.getPlayers().get(1);
        gameLoopUntilNextPhase(game);
        assertFalse(holdsReanimation(ai), zones(ai));
    }
}
