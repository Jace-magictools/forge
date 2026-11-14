package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 307 (D171 point 3, tester 12/11): Rancor went on Arbor Elf, a mana creature that never attacks.
// An aura that only adds power goes on a real attacker when there is one.
public class OffensiveAura307Test extends AITest {

    private String play(final String dork, final String attacker) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCards("Forest", 3, ai);
        addCard(dork, ai).setSickness(false);
        if (attacker != null) {
            addCard(attacker, ai).setSickness(false);
        }
        addCard("Wall of Wood", opponent);
        addCardToZone("Rancor", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        Card target = game.getCardsIn(ZoneType.Battlefield).stream()
                .filter(c -> c.getName().equals("Rancor")).findFirst().map(Card::getEnchantingCard).orElse(null);
        return target == null ? null : target.getName();
    }

    @Test
    public void testRancorOnAttacker() {
        for (String dork : new String[] {"Arbor Elf", "Llanowar Elves", "Elvish Mystic"}) {
            assertEquals(play(dork, "Savannah Lions"), "Savannah Lions", dork);
        }
    }

    @Test
    public void testRancorOnDorkWhenAlone() {
        assertEquals(play("Llanowar Elves", null), "Llanowar Elves");
    }
}
