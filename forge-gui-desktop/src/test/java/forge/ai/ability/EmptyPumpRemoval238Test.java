package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 238, relevé 6/11 (Baleful Mastery 0 cast, 6 refusals with mana and targets): a removal spell scripted
// as an empty Pump that only holds the target, with the Destroy/ChangeZone in its sub-abilities, was judged as a pump
public class EmptyPumpRemoval238Test extends AITest {

    private Game setup(String spell, String theirs, String... lands) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        for (String land : lands) {
            addCard(land, ai);
        }
        addCardToZone(spell, ai, ZoneType.Hand);
        if (theirs != null) {
            addCard(theirs, opponent);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        ai.setLandsPlayedThisTurn(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        return game;
    }

    @Test
    public void testSorceryDestroysTheirBest() {
        Game game = setup("Maelstrom Pulse", "Hill Giant", "Forest", "Swamp", "Swamp");
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Hill Giant", ZoneType.Battlefield), 0, gameStateToString(game));
    }

    @Test
    public void testInstantExilesInMain2() {
        Game game = setup("Baleful Mastery", "Hill Giant", "Swamp", "Swamp", "Swamp", "Swamp");
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Hill Giant", ZoneType.Exile), 1, gameStateToString(game));
    }

    @Test
    public void testBounceSpellPlayed() {
        Game game = setup("Echoing Truth", "Hill Giant", "Island", "Island");
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Hill Giant", ZoneType.Battlefield), 0, gameStateToString(game));
    }

    @Test
    public void testCurseBounceAlreadyPlayed() {
        Game game = setup("Sweep Away", "Hill Giant", "Island", "Island", "Island");
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Sweep Away", ZoneType.Hand), 0, gameStateToString(game));
    }

    @Test
    public void testNoTargetOfTheirsKeepsTheSpell() {
        Game game = setup("Maelstrom Pulse", null, "Forest", "Swamp", "Swamp");
        addCard("Grizzly Bears", game.getPlayers().get(1));
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Maelstrom Pulse", ZoneType.Hand), 1, gameStateToString(game));
        assertEquals(countCardsWithName(game, "Grizzly Bears", ZoneType.Battlefield), 1, gameStateToString(game));
    }
}
