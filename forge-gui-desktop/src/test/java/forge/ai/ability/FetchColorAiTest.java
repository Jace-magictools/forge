package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 36, a fetchland takes the basic the hand needs (Genesis, 27/10: Evolving Wilds fetched a
// Mountain with Island, Island and a green hand, stock AI broke ties in WUBRG order)
public class FetchColorAiTest extends AITest {

    @Test
    public void testFetchesMissingColorOfHand() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard("Island", ai);
        addCard("Island", ai);
        addCardToZone("Evolving Wilds", ai, ZoneType.Hand);
        addCardToZone("Lotus Cobra", ai, ZoneType.Hand);
        addCardToZone("Dryad of the Ilysian Grove", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        addCardToZone("Mountain", ai, ZoneType.Library);
        addCardToZone("Forest", ai, ZoneType.Library);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);

        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Evolving Wilds", ZoneType.Graveyard), 1, gameStateToString(game));
        assertEquals(countCardsWithName(game, "Forest", ZoneType.Battlefield), 1, gameStateToString(game));
    }

    // a red hand with two Forests: the Mountain, not the third Island-less tie
    @Test
    public void testFetchesRedForRedHand() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard("Forest", ai);
        addCard("Forest", ai);
        addCardToZone("Evolving Wilds", ai, ZoneType.Hand);
        addCardToZone("Bonecrusher Giant", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        addCardToZone("Island", ai, ZoneType.Library);
        addCardToZone("Mountain", ai, ZoneType.Library);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);

        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Mountain", ZoneType.Battlefield), 1, gameStateToString(game));
    }

    // rule 36b: nothing in hand to cast, the library's Genesis Ultimatum (GGUUURR) asks for a third blue source
    // before a first Forest; stock took the least present basic (Forest)
    @Test
    public void testFetchesForDeckHeaviestSpell() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard("Island", ai);
        addCard("Mountain", ai);
        addCard("Mountain", ai);
        addCardToZone("Evolving Wilds", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        addCardToZone("Genesis Ultimatum", ai, ZoneType.Library);
        addCardToZone("Island", ai, ZoneType.Library);
        addCardToZone("Forest", ai, ZoneType.Library);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);

        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Evolving Wilds", ZoneType.Graveyard), 1, gameStateToString(game));
        assertEquals(countCardsWithName(game, "Island", ZoneType.Battlefield), 2, gameStateToString(game));
    }

    // rule 36c (tester 27/10): a red hand already payable does not beat the Ultimatum's third blue source; stock and
    // rule 36 took the Mountain (Glorybringer RR asked for red)
    @Test
    public void testUltimatumBeforePayableHand() {
        Game game = ultimatumGame("Glorybringer");
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Evolving Wilds", ZoneType.Graveyard), 1, gameStateToString(game));
        assertEquals(countCardsWithName(game, "Island", ZoneType.Battlefield), 2, gameStateToString(game));
    }

    // rule 36c: a green card cast before the Ultimatum (Lotus Cobra) takes the Forest first
    @Test
    public void testEarlyGreenCardBeforeUltimatum() {
        Game game = ultimatumGame("Glorybringer", "Lotus Cobra");
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Evolving Wilds", ZoneType.Graveyard), 1, gameStateToString(game));
        assertEquals(countCardsWithName(game, "Forest", ZoneType.Battlefield), 1, gameStateToString(game));
    }

    private Game ultimatumGame(final String... hand) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard("Island", ai);
        addCard("Mountain", ai);
        addCard("Mountain", ai);
        addCardToZone("Evolving Wilds", ai, ZoneType.Hand);
        for (final String c : hand) {
            addCardToZone(c, ai, ZoneType.Hand);
        }
        fillLibrary(ai, 20);
        addCardToZone("Genesis Ultimatum", ai, ZoneType.Library);
        addCardToZone("Island", ai, ZoneType.Library);
        addCardToZone("Forest", ai, ZoneType.Library);
        addCardToZone("Mountain", ai, ZoneType.Library);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        return game;
    }
}
