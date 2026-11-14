package forge.ai.ability;

import java.util.List;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 135, a dig onto our battlefield skips a second copy of a legend we control
// (tournament v20, Pod: Birthing Ritual fetched a second Melira, the legend rule killed one)
public class LegendDig135Test extends AITest {

    private SpellAbility ritualDig(Card ritual, Player ai) {
        SpellAbility sa = ritual.getTriggers().iterator().next().ensureAbility();
        while (sa != null && !"Battlefield".equals(sa.getParam("DestinationZone"))) {
            sa = sa.getSubAbility();
        }
        sa.setActivatingPlayer(ai);
        return sa;
    }

    @Test
    public void testSkipsSecondMelira() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        addCard("Melira, Sylvok Outcast", ai);
        Card ritual = addCard("Birthing Ritual", ai);
        Card melira2 = addCardToZone("Melira, Sylvok Outcast", ai, ZoneType.Library);
        Card elves = addCardToZone("Llanowar Elves", ai, ZoneType.Library);
        Card chosen = new DigAi().chooseSingleCard(ai, ritualDig(ritual, ai), List.of(melira2, elves), true, ai, null);
        assertEquals(chosen, elves);
    }

    @Test
    public void testTakesLegendWhenNoneInPlay() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Card ritual = addCard("Birthing Ritual", ai);
        Card melira = addCardToZone("Melira, Sylvok Outcast", ai, ZoneType.Library);
        Card elves = addCardToZone("Llanowar Elves", ai, ZoneType.Library);
        Card chosen = new DigAi().chooseSingleCard(ai, ritualDig(ritual, ai), List.of(melira, elves), true, ai, null);
        assertEquals(chosen, melira);
    }
}
