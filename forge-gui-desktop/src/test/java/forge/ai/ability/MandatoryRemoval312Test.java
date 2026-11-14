package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.ability.AbilityKey;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertTrue;

// magic-tools rule 312 (D176, plateau 13/11, tester): Journey to Nowhere's mandatory "exile target creature" took
// the AI's own big creature because the only opposing one already wore its Planar Disruption. A mandatory removal
// with no good target takes an opposing card first, and its own (the least valuable) only when there is none.
public class MandatoryRemoval312Test extends AITest {

    @Test(timeOut = 120000)
    public void testNeutralisedOpponentBeforeOwn() {
        Game game = initAndCreateGame();
        Player opp = game.getPlayers().get(0);
        Player ai = game.getPlayers().get(1);
        addCard("Serra Angel", ai);
        Card bears = addCard("Grizzly Bears", opp);
        Card aura = addCard("Planar Disruption", ai);
        aura.attachToEntity(bears, null);
        for (Player p : game.getPlayers()) {
            fillLibrary(p, 20);
        }
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        Card journey = addCardToZone("Journey to Nowhere", ai, ZoneType.Hand);
        game.getAction().moveToPlay(journey, ai, null, AbilityKey.newMap());
        playUntilStackClear(game);
        assertTrue(countCardsWithName(game, "Serra Angel", ZoneType.Battlefield) == 1, "own Serra Angel exiled");
        assertTrue(countCardsWithName(game, "Grizzly Bears", ZoneType.Exile) == 1, "neutralised Grizzly Bears not exiled");
    }

    @Test(timeOut = 120000)
    public void testOnlyOwnTakesWorst() {
        Game game = initAndCreateGame();
        Player opp = game.getPlayers().get(0);
        Player ai = game.getPlayers().get(1);
        addCard("Serra Angel", ai);
        addCard("Healer's Hawk", ai);
        for (Player p : game.getPlayers()) {
            fillLibrary(p, 20);
        }
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        Card journey = addCardToZone("Journey to Nowhere", ai, ZoneType.Hand);
        game.getAction().moveToPlay(journey, ai, null, AbilityKey.newMap());
        playUntilStackClear(game);
        assertTrue(countCardsWithName(game, "Serra Angel", ZoneType.Battlefield) == 1, "Serra Angel exiled instead of the 1/1");
        assertTrue(countCardsWithName(game, "Healer's Hawk", ZoneType.Exile) == 1, "Healer's Hawk not exiled");
    }
}
