package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: "gain control until end of turn" (Claim the Firstborn) + sacrifice outlet: the stolen creature is sacrificed before it goes back
public class ThreatenSacAiTest extends AITest {

    private Game threatenGame(String... outlets) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard("Mountain", ai);
        addCard("Mountain", ai);
        addCard("Swamp", ai);
        for (String outlet : outlets) {
            addCard(outlet, ai);
        }
        addCardToZone("Claim the Firstborn", ai, ZoneType.Hand);
        addCard("Centaur Courser", opponent);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        return game;
    }

    @Test
    public void testStolenCreatureIsSacrificed() {
        Game game = threatenGame("Phyrexian Ghoul");
        playUntilNextTurn(game);
        assertEquals(countCardsWithName(game, "Claim the Firstborn", ZoneType.Graveyard), 1, gameStateToString(game));
        assertEquals(countCardsWithName(game, "Centaur Courser", ZoneType.Graveyard), 1, gameStateToString(game));
    }

    @Test
    public void testStolenCreatureIsSacrificedToCarrionFeeder() {
        Game game = threatenGame("Carrion Feeder");
        playUntilNextTurn(game);
        assertEquals(countCardsWithName(game, "Claim the Firstborn", ZoneType.Graveyard), 1, gameStateToString(game));
        assertEquals(countCardsWithName(game, "Centaur Courser", ZoneType.Graveyard), 1, gameStateToString(game));
    }

    // without a stolen creature, the Ghoul does not eat its own creatures after combat
    @Test
    public void testOwnCreatureIsKept() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard("Phyrexian Ghoul", ai);
        addCard("Grizzly Bears", ai);
        addCard("Centaur Courser", opponent);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        playUntilNextTurn(game);
        assertEquals(countCardsWithName(game, "Grizzly Bears", ZoneType.Battlefield), 1, gameStateToString(game));
    }

    // the Ghoul eats the stolen creature, not the AI's own Familiar
    @Test
    public void testStolenCreatureIsPaidFirst() {
        Game game = threatenGame("Phyrexian Ghoul", "Cauldron Familiar");
        playUntilNextTurn(game);
        assertEquals(countCardsWithName(game, "Centaur Courser", ZoneType.Graveyard), 1, gameStateToString(game));
        assertEquals(countCardsWithName(game, "Cauldron Familiar", ZoneType.Battlefield), 1, gameStateToString(game));
    }

    @Test
    public void testStolenCreatureIsSacrificedToOven() {
        Game game = threatenGame("Witch's Oven");
        playUntilNextTurn(game);
        assertEquals(countCardsWithName(game, "Centaur Courser", ZoneType.Graveyard), 1, gameStateToString(game));
    }

    @Test
    public void testStolenCreatureIsSacrificedToTymaret() {
        Game game = threatenGame("Tymaret, the Murder King");
        playUntilNextTurn(game);
        assertEquals(countCardsWithName(game, "Centaur Courser", ZoneType.Graveyard), 1, gameStateToString(game));
    }

    private Game noOutletGame(String... oppCreatures) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard("Mountain", ai);
        addCard("Mountain", ai);
        addCardToZone("Claim the Firstborn", ai, ZoneType.Hand);
        for (String c : oppCreatures) {
            addCard(c, opponent);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        return game;
    }

    // no outlet: a 1/1 is not worth the card
    @Test
    public void testNoOutletSmallCreatureKept() {
        Game game = noOutletGame("Llanowar Elves", "Grizzly Bears");
        playUntilNextTurn(game);
        assertEquals(countCardsWithName(game, "Claim the Firstborn", ZoneType.Hand), 1, gameStateToString(game));
    }

    // no outlet: a 3/3 still is
    @Test
    public void testNoOutletBigCreatureStolen() {
        Game game = noOutletGame("Centaur Courser", "Llanowar Elves");
        playUntilNextTurn(game);
        assertEquals(countCardsWithName(game, "Claim the Firstborn", ZoneType.Graveyard), 1, gameStateToString(game));
    }
}
