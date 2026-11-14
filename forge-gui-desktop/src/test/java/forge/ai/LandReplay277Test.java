package forge.ai;

import java.util.Arrays;

import org.testng.annotations.Test;

import forge.card.CardRarity;
import forge.card.CardRules;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;
import forge.item.PaperCard;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

// magic-tools rules 277 and 278 (D159, tester 10/11 12:53, plateau, deck cus_jrrt, a test game): the
// Ents deck stayed on 2-3 lands with three Jeune Enture in play and Ent de Fangorn never used.
// 277: two Forests, Jeune Enture, Centaur Courser in hand, no land in hand: Enture returns a Forest (+G), the Forest is
// played again, the Courser is cast with three mana. Control: a Forest in hand, Enture is left alone.
// 278: Skyshroud Ranger and a Forest in hand: the Ranger puts the Forest in, the land drop stays free.
public class LandReplay277Test extends AITest {

    private static final String[] ENTURE = {
            "Name:Jeune Enture",
            "ManaCost:G",
            "Types:Creature Treefolk Druid",
            "PT:1/2",
            "A:AB$ ChangeZone | Cost$ T | ValidTgts$ Land.YouOwn | Origin$ Battlefield | Destination$ Hand | SubAbility$ DBMana | SpellDescription$ Return target land you own from the battlefield to your hand. If that land is a Forest, add {G}.",
            "SVar:DBMana:DB$ Mana | Produced$ G | ConditionDefined$ Targeted | ConditionPresent$ Card.Forest | ConditionCompare$ GE1"
    };

    private Card perso(Player owner, ZoneType zone, String... script) {
        PaperCard pc = new PaperCard(CardRules.fromScript(Arrays.asList(script)), "M10", CardRarity.Common);
        Card c = Card.fromPaperCard(pc, owner);
        c.setGameTimestamp(owner.getGame().getNextTimestamp());
        owner.getZone(zone).add(c);
        return c;
    }

    private Game enture(boolean landInHand) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCards("Forest", 2, ai);
        perso(ai, ZoneType.Battlefield, ENTURE).setSickness(false);
        addCardToZone("Centaur Courser", ai, ZoneType.Hand);
        if (landInHand) {
            addCardToZone("Forest", ai, ZoneType.Hand);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        return game;
    }

    @Test(timeOut = 120000)
    public void testEntureReplaysAForest() {
        Game game = enture(false);
        Player ai = game.getPlayers().get(1);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Centaur Courser", ZoneType.Battlefield), 1, "Courser cast with 3 mana");
        assertEquals(countCardsWithName(game, "Forest", ZoneType.Battlefield), 2, "the Forest came back");
        assertEquals(ai.getLandsPlayedThisTurn(), 1, "replayed as the land drop");
    }

    @Test(timeOut = 120000)
    public void testLandInHandEntureWaits() {
        Game game = enture(true);
        Player ai = game.getPlayers().get(1);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Forest", ZoneType.Battlefield), 3, "Forest of the hand played");
        assertEquals(countCardsWithName(game, "Forest", ZoneType.Hand), 0, "no Forest returned");
    }

    @Test(timeOut = 120000)
    public void testRangerBeforeLandDrop() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCards("Forest", 2, ai);
        addCard("Skyshroud Ranger", ai).setSickness(false);
        addCardToZone("Forest", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        assertFalse(LandReplayAi.beforeLand(ai).isEmpty(), "Ranger offered before the land drop");
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Forest", ZoneType.Battlefield), 3, "Forest put in");
        assertEquals(ai.getLandsPlayedThisTurn(), 0, "by the Ranger, the land drop is still free");
        assertTrue(game.getCardsIn(ZoneType.Battlefield).stream()
                .anyMatch(c -> c.getName().equals("Skyshroud Ranger") && c.isTapped()), "Ranger used");
    }
}
