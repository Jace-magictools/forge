package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 239, relevé 6/11 (Severed Strands 4 refusals with mana, a Goat token and targets): the life gained
// is read from the sacrificed card, so it is 0 until the cost is paid and the spell was refused for "no life to gain"
public class SacrificedAmount239Test extends AITest {

    private Game setup(String spell, String ours, String theirs, String... lands) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        for (String land : lands) {
            addCard(land, ai);
        }
        addCardToZone(spell, ai, ZoneType.Hand);
        if (ours != null) {
            addToken(ours, ai);
        }
        if (theirs != null) {
            addCard(theirs, opponent);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        ai.setLandsPlayedThisTurn(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        return game;
    }

    @Test
    public void testStrandsSacrificesTokenAndDestroys() {
        Game game = setup("Severed Strands", "w_0_1_goat", "Hill Giant", "Swamp", "Swamp");
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Hill Giant", ZoneType.Battlefield), 0, gameStateToString(game));
    }

    @Test
    public void testStrandsKeptWithoutTarget() {
        Game game = setup("Severed Strands", "w_0_1_goat", null, "Swamp", "Swamp");
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Severed Strands", ZoneType.Hand), 1, gameStateToString(game));
    }

    @Test
    public void testStrandsKeptWithoutFodder() {
        Game game = setup("Severed Strands", null, "Hill Giant", "Swamp", "Swamp");
        addCard("Grizzly Bears", game.getPlayers().get(1));
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Grizzly Bears", ZoneType.Battlefield), 1, gameStateToString(game));
    }

    @Test
    public void testBargainDrawsWithToken() {
        // an instant: Forge's lifegain timing keeps it for the end of the opponent's turn
        Game game = setup("Reckoner's Bargain", "w_0_1_goat", null, "Swamp", "Swamp");
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, game.getPlayers().get(0));
        playUntilPhase(game, PhaseType.CLEANUP);
        assertEquals(countCardsWithName(game, "Reckoner's Bargain", ZoneType.Hand), 0, gameStateToString(game));
    }
}
