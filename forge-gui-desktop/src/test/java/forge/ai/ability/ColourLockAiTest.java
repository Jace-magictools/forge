package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertTrue;

/**
 * magic-tools: rule 37, Profanation (tester 27/10): Iona against a mono-coloured deck (or a two-colour deck leaning
 * on one colour), otherwise Griselbrand.
 */
public class ColourLockAiTest extends AITest {

    private static final String[] MONO_RED = {"Lightning Bolt"};
    private static final String[] FIVE_COLOURS = {"Lightning Bolt", "Giant Growth", "Counterspell", "Doom Blade", "Swords to Plowshares"};

    private Game setUp(String spell, int swamps, String[] oppSpells, boolean inGraveyard) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        for (int i = 0; i < swamps; i++) {
            addCard("Swamp", ai);
        }
        addCardToZone(spell, ai, ZoneType.Hand);
        ZoneType fatties = inGraveyard ? ZoneType.Graveyard : ZoneType.Library;
        addCardToZone("Griselbrand", ai, fatties);
        addCardToZone("Iona, Shield of Emeria", ai, fatties);
        for (int i = 0; i < 10; i++) {
            addCardToZone("Swamp", ai, ZoneType.Library);
        }
        for (int i = 0; i < 20; i++) {
            addCardToZone(oppSpells[i % oppSpells.length], opponent, ZoneType.Library);
        }
        moveToMain2(game, ai);
        return game;
    }

    private static String zones(Player ai) {
        return "graveyard=" + ai.getCardsIn(ZoneType.Graveyard) + " battlefield=" + ai.getCardsIn(ZoneType.Battlefield);
    }

    private static boolean has(Player ai, ZoneType zone, String name) {
        return ai.getCardsIn(zone).anyMatch(c -> c.getName().equals(name));
    }

    @Test
    public void testEntombBuriesIonaAgainstMonoColour() {
        Game game = setUp("Entomb", 1, MONO_RED, false);
        Player ai = game.getPlayers().get(1);
        gameLoopUntilNextPhase(game);
        assertTrue(has(ai, ZoneType.Graveyard, "Iona, Shield of Emeria"), zones(ai));
    }

    @Test
    public void testEntombBuriesGriselbrandAgainstFiveColours() {
        Game game = setUp("Entomb", 1, FIVE_COLOURS, false);
        Player ai = game.getPlayers().get(1);
        gameLoopUntilNextPhase(game);
        assertTrue(has(ai, ZoneType.Graveyard, "Griselbrand"), zones(ai));
    }

    @Test
    public void testReanimateTakesGriselbrandAgainstFiveColours() {
        // without the rule the target is the most expensive one, Iona (9 against 8)
        Game game = setUp("Reanimate", 1, FIVE_COLOURS, true);
        Player ai = game.getPlayers().get(1);
        gameLoopUntilNextPhase(game);
        assertTrue(has(ai, ZoneType.Battlefield, "Griselbrand"), zones(ai));
    }

    @Test
    public void testAnimateDeadTakesIonaAgainstMonoColour() {
        Game game = setUp("Animate Dead", 2, MONO_RED, true);
        Player ai = game.getPlayers().get(1);
        gameLoopUntilNextPhase(game);
        assertTrue(has(ai, ZoneType.Battlefield, "Iona, Shield of Emeria"), zones(ai));
    }
}
