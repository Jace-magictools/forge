package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.CardCollectionView;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools, règle 91 : ElfBall gardait l'Annonciatrice elfique au mulligan (tester 29/10 11:54)
public class MulliganTuck91Test extends AITest {

    private Player elfDeck(String... hand) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        for (int i = 0; i < 10; i++) {
            addCardToZone("Forest", ai, ZoneType.Library);
        }
        for (int i = 0; i < 40; i++) {
            addCardToZone("Llanowar Elves", ai, ZoneType.Library);
        }
        for (String name : hand) {
            addCardToZone(name, ai, ZoneType.Hand);
        }
        return ai;
    }

    private String tucked(Player ai) {
        CardCollectionView back = ai.getController().tuckCardsViaMulligan(ai.getCardsIn(ZoneType.Hand), 1);
        assertEquals(back.size(), 1);
        Card c = back.getFirst();
        return c.getName();
    }

    // un terrain, la main vit des elfes à mana : l'Annonciatrice (3 mana, remplace une pioche) part, pas un elfe à 1
    @Test
    public void testTucksTopTutorWhenShortOfLands() {
        Player ai = elfDeck("Elvish Archdruid", "Leaf-Crowned Visionary", "Forest", "Elvish Mystic",
                "Elvish Harbinger", "Heritage Druid", "Llanowar Elves");
        assertEquals(tucked(ai), "Elvish Harbinger");
    }

    // témoin : sans tuteur, le sort le plus cher qui ne fait pas de mana à bas coût part
    @Test
    public void testTucksMostExpensiveWithoutTutor() {
        Player ai = elfDeck("Elvish Archdruid", "Leaf-Crowned Visionary", "Forest", "Elvish Mystic",
                "Beast Whisperer", "Heritage Druid", "Llanowar Elves");
        assertEquals(tucked(ai), "Beast Whisperer");
    }
}
