package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools, règle 206 : « engage N du type » à portée, les petits sorts du type d'abord, les terrains paient
public class TapGoal206Test extends AITest {

    private Game goalGame(String[] inPlay, String... hand) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        for (String name : inPlay) {
            addCard(name, ai).setSickness(false);
        }
        addCard("Mountain", opponent);
        addCard("Mountain", opponent);
        for (String name : hand) {
            addCardToZone(name, ai, ZoneType.Hand);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        return game;
    }

    // trois petits Druides suffisent, les terrains les paient : les terres adverses tombent ce tour, pas le 2e Feuilldor
    @Test
    public void testTroisPetitsEtLesTerrains() {
        Game game = goalGame(new String[] {"Forest", "Forest", "Forest", "Llanowar Elves", "Elvish Archdruid",
                "Gilt-Leaf Archdruid", "Fyndhorn Elves"},
                "Gilt-Leaf Archdruid", "Heritage Druid", "Arbor Elf", "Elvish Mystic");
        Player ai = game.getPlayers().get(1);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(ai.getCardsIn(ZoneType.Battlefield).stream().filter(c -> "Mountain".equals(c.getName())).count(), 2L, gameStateToString(game));
    }

    // but hors de portée : l'ordre habituel, le gros sort passe
    @Test
    public void testHorsDePortee() {
        Game game = goalGame(new String[] {"Forest", "Llanowar Elves", "Elvish Archdruid", "Gilt-Leaf Archdruid", "Fyndhorn Elves"},
                "Gilt-Leaf Archdruid", "Heritage Druid");
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Gilt-Leaf Archdruid", ZoneType.Battlefield), 2, gameStateToString(game));
    }
}
