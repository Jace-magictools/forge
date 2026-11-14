package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 308 (tester 12/11, D171 point 4): with Arbor Elf ("{T}: untap target Forest"), the second
// Wild Growth goes on the Forest that already has one: each untap then gives the bonus of both auras.
public class ManaAuraStack308Test extends AITest {

    private Game play(final boolean enchantedFirst, final boolean elf) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        Card plainFirst = enchantedFirst ? null : addCard("Forest", ai);
        Card grown = addCard("Forest", ai);
        Card wg = addCard("Wild Growth", ai);
        wg.attachToEntity(grown, null);
        if (enchantedFirst) {
            addCard("Forest", ai);
        }
        addCard("Forest", ai);
        if (elf) {
            addCard("Arbor Elf", ai).setSickness(false);
        }
        addCardToZone("Wild Growth", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        return game;
    }

    private static int maxStack(Game game) {
        int max = 0;
        for (Card c : game.getCardsIn(ZoneType.Battlefield)) {
            if (c.getName().equals("Forest")) {
                max = Math.max(max, (int) c.getEnchantedBy().stream()
                        .filter(a -> a.getName().equals("Wild Growth")).count());
            }
        }
        return max;
    }

    @Test
    public void testStackWithArborElf() {
        for (boolean first : new boolean[] {true, false}) {
            Game game = play(first, true);
            assertEquals(maxStack(game), 2, first + " " + gameStateToString(game));
        }
    }

    @Test
    public void testNoElfNoMatter() {
        // without an untapper, rule 308 has no opinion: the second aura is cast somewhere
        Game game = play(false, false);
        int auras = 0;
        for (Card c : game.getCardsIn(ZoneType.Battlefield)) {
            if (c.getName().equals("Wild Growth")) {
                auras++;
            }
        }
        assertEquals(auras, 2, gameStateToString(game));
    }
}
