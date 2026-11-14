package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

// magic-tools: Birthing Pod fetches the missing piece of the Melira + Murderous Redcap + sacrifice outlet loop
public class PodComboAiTest extends AITest {

    private Game podGame(String... battlefield) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard("Birthing Pod", ai);
        addCard("Forest", ai);
        addCard("Forest", ai);
        for (String name : battlefield) {
            addCard(name, ai);
        }
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        return game;
    }

    // stock AI: waits for main 2 and casts the Bears first; Finks (3) upgrades into the Redcap (4), not the Hill Giant
    @Test
    public void testPodFetchesRedcapInMain1() {
        Game game = podGame("Melira, Sylvok Outcast", "Viscera Seer", "Kitchen Finks");
        Player ai = game.getPlayers().get(1);
        addCardToZone("Grizzly Bears", ai, ZoneType.Hand);
        for (int i = 0; i < 3; i++) {
            addCardToZone("Hill Giant", ai, ZoneType.Library);
        }
        addCardToZone("Murderous Redcap", ai, ZoneType.Library);
        fillLibrary(ai, 20);
        game.getAction().checkStateEffects(true);

        playUntilPhase(game, PhaseType.COMBAT_BEGIN);
        assertTrue(countCardsWithName(game, "Murderous Redcap", ZoneType.Battlefield) == 1 || game.isGameOver(),
                gameStateToString(game));
        assertEquals(countCardsWithName(game, "Melira, Sylvok Outcast", ZoneType.Battlefield), 1);
        assertEquals(countCardsWithName(game, "Hill Giant", ZoneType.Battlefield), 0);
    }

    @Test
    public void testPodLoopKills() {
        Game game = podGame("Melira, Sylvok Outcast", "Viscera Seer", "Kitchen Finks");
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCardToZone("Hill Giant", ai, ZoneType.Library);
        addCardToZone("Murderous Redcap", ai, ZoneType.Library);
        fillLibrary(ai, 20);
        game.getAction().checkStateEffects(true);

        playUntilNextTurn(game);
        assertTrue(opponent.getLife() <= 0, "life " + opponent.getLife());
    }

    // Redcap and Seer in play: sacrifice the Elves (not the Seer) for Melira
    @Test
    public void testPodFetchesMelira() {
        Game game = podGame("Murderous Redcap", "Viscera Seer", "Llanowar Elves");
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCardToZone("Melira, Sylvok Outcast", ai, ZoneType.Library);
        fillLibrary(ai, 20);
        game.getAction().checkStateEffects(true);

        playUntilNextTurn(game);
        assertEquals(countCardsWithName(game, "Llanowar Elves", ZoneType.Graveyard), 1);
        assertTrue(opponent.getLife() <= 0, "life " + opponent.getLife());
    }

    private Game comboGame(String spell, int forests) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        for (int i = 0; i < forests; i++) {
            addCard("Forest", ai);
        }
        addCard("Melira, Sylvok Outcast", ai);
        addCard("Viscera Seer", ai);
        addCard("Kitchen Finks", ai);
        addCardToZone(spell, ai, ZoneType.Hand);
        for (int i = 0; i < 3; i++) {
            addCardToZone("Thragtusk", ai, ZoneType.Library);
        }
        addCardToZone("Murderous Redcap", ai, ZoneType.Library);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        return game;
    }

    // Worldly Tutor puts the Redcap (not the Thragtusk) on top, and the loop runs the turn after
    @Test
    public void testWorldlyTutorFindsRedcap() {
        Game game = comboGame("Worldly Tutor", 5);
        Player opponent = game.getPlayers().get(0);
        playUntilNextTurn(game);
        playUntilNextTurn(game);
        assertEquals(countCardsWithName(game, "Worldly Tutor", ZoneType.Graveyard), 1, gameStateToString(game));
        Player ai = game.getPlayers().get(1);
        assertTrue(opponent.getLife() <= 0 || countCardsWithName(game, "Murderous Redcap", ZoneType.Library) == 0
                || "Murderous Redcap".equals(ai.getCardsIn(ZoneType.Library).get(0).getName()), gameStateToString(game));
    }

    // Eldritch Evolution sacrifices the Finks and fetches the Redcap
    @Test
    public void testEldritchEvolutionFetchesRedcap() {
        Game game = comboGame("Eldritch Evolution", 3);
        Player opponent = game.getPlayers().get(0);
        playUntilNextTurn(game);
        assertEquals(countCardsWithName(game, "Thragtusk", ZoneType.Battlefield), 0, gameStateToString(game));
        assertEquals(countCardsWithName(game, "Melira, Sylvok Outcast", ZoneType.Battlefield), 1);
        assertTrue(opponent.getLife() <= 0, "life " + opponent.getLife());
    }
}
