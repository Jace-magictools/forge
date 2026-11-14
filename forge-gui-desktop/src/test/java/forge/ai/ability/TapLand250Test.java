package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 250 (a tester report 08/11, D143): the untapped land only when its mana is spent this turn.
// Turn 1 with Curiosity and no creature, Island came down instead of Halimar Depths (a test game);
// turn 7 with Prince, Talisman, Guardmage, Brago and five mana, Bad River came down tapped instead of the Plains
// that made six (Guardmage + Talisman, a test game)
public class TapLand250Test extends AITest {

    private Game setup(String[] battlefield, String... hand) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        for (String c : battlefield) {
            addCard(c, ai);
        }
        for (String c : hand) {
            addCardToZone(c, ai, ZoneType.Hand);
        }
        for (int i = 0; i < 4; i++) {
            addCardToZone("Island", ai, ZoneType.Library);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        return game;
    }

    @Test
    public void testHalimarWhenTheAuraHasNoHost() {
        Game game = setup(new String[] {}, "Curiosity", "Island", "Curiosity", "Gelectrode", "Halimar Depths", "Impulse");
        assertEquals(countCardsWithName(game, "Halimar Depths", ZoneType.Battlefield), 1, gameStateToString(game));
        assertEquals(countCardsWithName(game, "Island", ZoneType.Hand), 1, gameStateToString(game));
    }

    @Test
    public void testIslandWhenTheAuraHasAHost() {
        Game game = setup(new String[] {"Grizzly Bears"}, "Curiosity", "Island", "Gelectrode", "Halimar Depths", "Impulse");
        assertEquals(countCardsWithName(game, "Island", ZoneType.Battlefield), 1, gameStateToString(game));
        assertEquals(countCardsWithName(game, "Halimar Depths", ZoneType.Hand), 1, gameStateToString(game));
    }

    @Test
    public void testPlainsWhenTheSixthManaIsSpent() {
        Game game = setup(new String[] {"Underground River", "Swamp", "Caves of Koilos", "Plains", "Talisman of Hierarchy"},
                "Elite Guardmage", "Brago, King Eternal", "Charming Prince", "Talisman of Progress", "Plains", "Bad River");
        assertEquals(countCardsWithName(game, "Bad River", ZoneType.Hand), 1, gameStateToString(game));
        assertEquals(countCardsWithName(game, "Plains", ZoneType.Battlefield), 2, gameStateToString(game));
        // Guardmage cast (Charming Prince may blink it into exile until the end step)
        assertEquals(countCardsWithName(game, "Elite Guardmage", ZoneType.Hand), 0, gameStateToString(game));
    }
}
