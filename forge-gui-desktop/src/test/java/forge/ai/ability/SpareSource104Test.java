package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.ai.ComputerUtilMana;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.mana.ManaCostBeingPaid;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

// magic-tools: rule 104 (a tester report 29/10 19:14, Kavaron - Nothing Gulch turn 6): Sol Ring paid with red while
// Fountainport, whose other abilities could not be paid this turn, stayed untapped
public class SpareSource104Test extends AITest {

    private CardCollection paySolRing(String... lands) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        for (String l : lands) {
            Card c = addCard(l, ai);
            c.setTapped(false);
        }
        Card ring = addCardToZone("Sol Ring", ai, ZoneType.Hand);
        addCardToZone("Legion Extruder", ai, ZoneType.Hand);
        addCardToZone("Breya's Apprentice", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        SpellAbility sa = ring.getFirstSpellAbility();
        sa.setActivatingPlayer(ai);
        return ComputerUtilMana.getManaSourcesToPayCost(new ManaCostBeingPaid(ring.getManaCost()), sa, ai, false);
    }

    private boolean has(CardCollection cards, String name) {
        return cards.stream().anyMatch(c -> c.getName().equals(name));
    }

    @Test
    public void testFountainportPaysWhenItsAbilitiesAreOutOfReach() {
        CardCollection paid = paySolRing("Great Furnace", "Fountainport", "Mountain");
        assertEquals(paid.size(), 1);
        assertTrue(has(paid, "Fountainport"), paid.toString());
    }

    @Test
    public void testFountainportKeptWhenItsAbilityIsAffordable() {
        CardCollection paid = paySolRing("Great Furnace", "Fountainport", "Mountain", "Mountain", "Mountain", "Mountain");
        assertEquals(paid.size(), 1);
        assertFalse(has(paid, "Fountainport"), paid.toString());
    }
}
