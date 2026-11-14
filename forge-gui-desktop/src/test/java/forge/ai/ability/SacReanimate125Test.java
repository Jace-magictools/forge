package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 125, Doomed Necromancer sacrifices itself to return a much better creature
// (tournament v18, Profanation: 18 games on the battlefield, 0 activation with Akroma / Iona in the graveyard)
public class SacReanimate125Test extends AITest {

    private Game necromancerWith(String inGraveyard) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard("Doomed Necromancer", ai).setSickness(false);
        addCardToZone(inGraveyard, ai, ZoneType.Graveyard);
        addCard("Swamp", ai);
        addCard("Swamp", ai);
        addCard("Plains", opponent);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        return game;
    }

    @Test
    public void testReturnsAkroma() {
        Game game = necromancerWith("Akroma, Angel of Wrath");
        assertEquals(countCardsWithName(game, "Akroma, Angel of Wrath", ZoneType.Battlefield), 1, gameStateToString(game));
    }

    @Test
    public void testKeepsNecromancerForABear() {
        Game game = necromancerWith("Grizzly Bears");
        assertEquals(countCardsWithName(game, "Doomed Necromancer", ZoneType.Battlefield), 1, gameStateToString(game));
    }
}
