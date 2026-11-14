package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 38, a fetchland is worth the lands it can find (Genesis 27/10: Evolving Wilds kept on turns 1
// and 2 while an Island came down with nothing to cast; Profanation: Plains played, Marsh Flats kept)
public class FetchLandPlayAiTest extends AITest {

    @Test
    public void testPlaysWildsOverBasicWithNothingToCast() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard("Island", ai);
        addCardToZone("Island", ai, ZoneType.Hand);
        addCardToZone("Evolving Wilds", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        addCardToZone("Forest", ai, ZoneType.Library);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);

        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Evolving Wilds", ZoneType.Graveyard), 1, gameStateToString(game));
        assertEquals(countCardsWithName(game, "Island", ZoneType.Hand), 1, gameStateToString(game));
    }

    @Test
    public void testPlaysMarshFlatsOverPlains() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard("Plains", ai);
        addCardToZone("Plains", ai, ZoneType.Hand);
        addCardToZone("Marsh Flats", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        addCardToZone("Swamp", ai, ZoneType.Library);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);

        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Plains", ZoneType.Hand), 1, gameStateToString(game));
        assertEquals(countCardsWithName(game, "Swamp", ZoneType.Battlefield), 1, gameStateToString(game));
    }

    // a two-drop in hand needs the land drop now: the untapped Island comes first, Wilds waits
    @Test
    public void testBasicFirstWhenItCastsASpell() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard("Island", ai);
        addCardToZone("Island", ai, ZoneType.Hand);
        addCardToZone("Evolving Wilds", ai, ZoneType.Hand);
        addCardToZone("Ledger Shredder", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        addCardToZone("Forest", ai, ZoneType.Library);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);

        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Evolving Wilds", ZoneType.Hand), 1, gameStateToString(game));
        assertEquals(countCardsWithName(game, "Ledger Shredder", ZoneType.Battlefield), 1, gameStateToString(game));
    }
}
