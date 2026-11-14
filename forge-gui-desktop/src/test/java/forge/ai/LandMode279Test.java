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

// magic-tools rule 279 (D161, tester 10/11 14:13, plateau, Gardien de la Forêt de cus_jrrt, a test game):
// the arrival "choose one" took "return a Forest" at random and returned nothing, a Forest in hand.
// 1) Forest in hand, land drop used: the Forest of the hand is put onto the battlefield.
// 2) no land in hand, a Forest in the graveyard, land drop used: the Forest of the graveyard comes back to the hand.
public class LandMode279Test extends AITest {

    private static final String[] GARDIEN = {
            "Name:Gardien de la Forêt",
            "ManaCost:1 G",
            "Types:Creature Treefolk Shaman",
            "PT:1/4",
            "T:Mode$ ChangesZone | Origin$ Any | Destination$ Battlefield | ValidCard$ Card.Self | OptionalDecider$ You | Execute$ TrigCharm | TriggerDescription$ When CARDNAME enters, ABILITY",
            "SVar:TrigCharm:DB$ Charm | Choices$ DBPut,DBReturn",
            "SVar:DBPut:DB$ ChangeZone | Origin$ Hand | Destination$ Battlefield | ChangeType$ Forest.YouOwn | SpellDescription$ You may put a Forest card from your hand onto the battlefield.",
            "SVar:DBReturn:DB$ ChangeZone | Hidden$ True | Origin$ Battlefield,Graveyard | Destination$ Hand | ChangeType$ Forest.YouOwn | SpellDescription$ Return a Forest card you own from the battlefield or your graveyard to your hand."
    };

    private Card perso(Player owner, ZoneType zone, String... script) {
        PaperCard pc = new PaperCard(CardRules.fromScript(Arrays.asList(script)), "M10", CardRarity.Common);
        Card c = Card.fromPaperCard(pc, owner);
        c.setGameTimestamp(owner.getGame().getNextTimestamp());
        owner.getZone(zone).add(c);
        return c;
    }

    private Game gardien(boolean forestInHand) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCards("Forest", 2, ai);
        perso(ai, ZoneType.Hand, GARDIEN);
        addCardToZone("Forest", ai, forestInHand ? ZoneType.Hand : ZoneType.Graveyard);
        ai.addLandPlayedThisTurn();
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        return game;
    }

    @Test(timeOut = 120000)
    public void testPutsTheForestOfTheHand() {
        Game game = gardien(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Gardien de la Forêt", ZoneType.Battlefield), 1, "Gardien cast");
        assertEquals(countCardsWithName(game, "Forest", ZoneType.Battlefield), 3, "Forest of the hand put in");
        assertEquals(countCardsWithName(game, "Forest", ZoneType.Hand), 0, "nothing left in hand");
    }

    @Test(timeOut = 120000)
    public void testTakesBackTheForestOfTheGraveyard() {
        Game game = gardien(false);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Gardien de la Forêt", ZoneType.Battlefield), 1, "Gardien cast");
        assertEquals(countCardsWithName(game, "Forest", ZoneType.Graveyard), 0, "Forest left the graveyard");
        assertEquals(countCardsWithName(game, "Forest", ZoneType.Hand), 1, "Forest back in hand");
        assertEquals(countCardsWithName(game, "Forest", ZoneType.Battlefield), 2, "no Forest of the battlefield returned");
    }
}
