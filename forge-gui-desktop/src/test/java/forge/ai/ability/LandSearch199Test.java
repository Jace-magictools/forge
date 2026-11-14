package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.ai.LandSearchAi;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 199, a tester report 02/11 (a test game): Expedition Map, Glimmerpost in hand, Cloudpost to find.
public class LandSearch199Test extends AITest {

    private CardCollection library(Player ai, String... names) {
        CardCollection lands = new CardCollection();
        for (String n : names) {
            lands.add(addCardToZone(n, ai, ZoneType.Library));
        }
        return lands;
    }

    /** The report: more Locus in hand, Cloudpost makes more mana than a utility land or a basic */
    @Test
    public void testCloudpostWithALocusInHand() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        addCardToZone("Glimmerpost", ai, ZoneType.Hand);
        addCard("Island", ai);
        CardCollection lands = library(ai, "Mutavault", "Cloudpost", "Island", "Field of Ruin", "Ghost Quarter");
        assertEquals(LandSearchAi.choose(ai, lands, false).getName(), "Cloudpost");
    }

    /** No Locus around: Cloudpost alone is a tapped one-mana land, the color the hand lacks comes first */
    @Test
    public void testColorTheHandLacks() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        addCard("Island", ai);
        addCardToZone("Lightning Bolt", ai, ZoneType.Hand);
        CardCollection lands = library(ai, "Cloudpost", "Island", "Ghost Quarter", "Mountain");
        assertEquals(LandSearchAi.choose(ai, lands, false).getName(), "Mountain");
    }

    /** A legendary land we already hold stays in the library */
    @Test
    public void testNoSecondLegendaryLand() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        addCard("Island", ai);
        addCardToZone("Eiganjo Castle", ai, ZoneType.Hand);
        Card picked = LandSearchAi.choose(ai, library(ai, "Eiganjo Castle", "Ghost Quarter"), false);
        assertEquals(picked.getName(), "Ghost Quarter");
    }

    /** Through the real search: the Map's ability in ChangeZoneAi */
    @Test
    public void testExpeditionMapPath() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        addCard("Island", ai);
        addCardToZone("Glimmerpost", ai, ZoneType.Hand);
        CardCollection lands = library(ai, "Mutavault", "Cloudpost", "Ghost Quarter");
        Card map = addCard("Expedition Map", ai);
        forge.game.spellability.SpellAbility sa = map.getSpellAbilities().get(0);
        sa.setActivatingPlayer(ai);
        Card picked = ChangeZoneAi.chooseCardToHiddenOriginChangeZone(ZoneType.Hand,
                java.util.List.of(ZoneType.Library), sa, lands, ai, ai);
        assertEquals(picked.getName(), "Cloudpost");
    }
}
