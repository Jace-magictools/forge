package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 113 (a tester report 30/10 18:16, Schizoph - Profanation turn 5): paying Rakdos Headliner's echo,
// the AI discards Stromkirk Occultist and casts it for madness instead of a spare land (three in hand)
public class MadnessDiscard113Test extends AITest {

    private Game setup(boolean manaForMadness) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        addCard("Swamp", ai);
        if (manaForMadness) {
            addCard("Mountain", ai);
        }
        Card headliner = addCard("Rakdos Headliner", ai);
        headliner.setSickness(false);
        // came under our control since our last upkeep: echo is due
        headliner.setCameUnderControlSinceLastUpkeep(true);
        addCardToZone("Stromkirk Occultist", ai, ZoneType.Hand);
        addCardToZone("Mountain", ai, ZoneType.Hand);
        addCardToZone("Mountain", ai, ZoneType.Hand);
        addCardToZone("Bone Shards", ai, ZoneType.Hand);
        addCardToZone("Restless Vents", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.UNTAP, ai);
        game.getAction().checkStateEffects(true);
        return game;
    }

    @Test
    public void testDiscardsMadnessCardAndCastsIt() {
        Game game = setup(true);
        playUntilPhase(game, PhaseType.DRAW);
        assertEquals(countCardsWithName(game, "Stromkirk Occultist", ZoneType.Battlefield), 1, gameStateToString(game));
        assertEquals(countCardsWithName(game, "Rakdos Headliner", ZoneType.Battlefield), 1, gameStateToString(game));
    }

    @Test
    public void testNoManaKeepsMadnessCard() {
        Game game = setup(false);
        playUntilPhase(game, PhaseType.DRAW);
        assertEquals(countCardsWithName(game, "Stromkirk Occultist", ZoneType.Battlefield), 0, gameStateToString(game));
    }
}
