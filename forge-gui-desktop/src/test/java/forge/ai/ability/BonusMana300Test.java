package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 300 (tester 12/11, D169): Barkhide Troll paid with Forest + the Forest under Wild Growth, one {G}
// lost. With the bonus land alone paying {G}{G}, the plain Forest stays untapped (open for an instant, a later ability).
public class BonusMana300Test extends AITest {

    private Game play(boolean enchantFirst) {
        return play(enchantFirst, "Barkhide Troll");
    }

    private Game play(boolean enchantFirst, String spell) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        Card a = addCard("Forest", ai);
        Card b = addCard("Forest", ai);
        Card growth = addCard("Wild Growth", ai);
        growth.attachToEntity(enchantFirst ? a : b, null);
        addCardToZone(spell, ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        return game;
    }

    private static int untappedForests(Game game) {
        int n = 0;
        for (Card c : game.getPlayers().get(1).getCardsIn(ZoneType.Battlefield)) {
            if (c.getName().equals("Forest") && !c.isTapped()) {
                n++;
            }
        }
        return n;
    }

    @Test
    public void testBonusLandFirstEnchantedFirst() {
        Game game = play(true);
        assertEquals(countCardsWithName(game, "Barkhide Troll", ZoneType.Battlefield), 1, gameStateToString(game));
        assertEquals(untappedForests(game), 1, gameStateToString(game));
    }

    @Test
    public void testBonusLandFirstEnchantedSecond() {
        Game game = play(false);
        assertEquals(countCardsWithName(game, "Barkhide Troll", ZoneType.Battlefield), 1, gameStateToString(game));
        assertEquals(untappedForests(game), 1, gameStateToString(game));
    }

    // a {G} spell: the bonus land would lose a {G}, the plain Forest pays and the enchanted one stays untapped
    @Test
    public void testPlainLandWhenBonusWasted() {
        for (boolean first : new boolean[] {true, false}) {
            Game game = play(first, "Llanowar Elves");
            assertEquals(countCardsWithName(game, "Llanowar Elves", ZoneType.Battlefield), 1, gameStateToString(game));
            Card enchanted = game.getCardsIn(ZoneType.Battlefield).stream()
                    .filter(c -> c.getName().equals("Forest") && c.isEnchanted()).findFirst().orElse(null);
            assertEquals(enchanted != null && !enchanted.isTapped(), true, gameStateToString(game));
        }
    }
    // D171 point 5 (tester 12/11): Rhonas the Indomitable ({2}{G}) paid with 4 lands. Four Forests, Wild Growth on
    // one (or on two): the enchanted Forest and one more pay the 3 mana, two Forests stay untapped.
    @Test
    public void testNoWasteThreeManaSpell() {
        for (int growths = 1; growths <= 2; growths++) {
            Game game = initAndCreateGame();
            Player ai = game.getPlayers().get(1);
            Player opponent = game.getPlayers().get(0);
            Card[] f = new Card[4];
            for (int k = 0; k < 4; k++) {
                f[k] = addCard("Forest", ai);
            }
            for (int g = 0; g < growths; g++) {
                addCard("Wild Growth", ai).attachToEntity(f[3 - g], null);
            }
            addCardToZone("Rhonas the Indomitable", ai, ZoneType.Hand);
            fillLibrary(ai, 20);
            fillLibrary(opponent, 20);
            game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
            game.getAction().checkStateEffects(true);
            playUntilPhase(game, PhaseType.END_OF_TURN);
            assertEquals(countCardsWithName(game, "Rhonas the Indomitable", ZoneType.Battlefield), 1, gameStateToString(game));
            assertEquals(untappedForests(game), 2, "growths=" + growths + " " + gameStateToString(game));
        }
    }
}
