package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 117, keep the mana of an instant creature removal while the opponent has no creature yet
public class RemovalMana117Test extends AITest {

    private Game ourTurn(int swamps, boolean oppLostCreature, String... hand) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        for (int i = 0; i < swamps; i++) {
            addCard("Swamp", ai);
        }
        if (oppLostCreature) {
            addCardToZone("Grizzly Bears", opponent, ZoneType.Graveyard);
        }
        for (String c : hand) {
            addCardToZone(c, ai, ZoneType.Hand);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        return game;
    }

    @Test
    public void testKeepsDoomBladeMana() {
        Game game = ourTurn(2, true, "Doom Blade", "Child of Night");
        assertEquals(countCardsWithName(game, "Child of Night", ZoneType.Battlefield), 0, gameStateToString(game));
    }

    @Test
    public void testCastsWhenRemovalStaysPayable() {
        Game game = ourTurn(4, true, "Doom Blade", "Child of Night");
        assertEquals(countCardsWithName(game, "Child of Night", ZoneType.Battlefield), 1, gameStateToString(game));
    }

    // no creature seen from the opponent yet: maybe they play none, develop
    @Test
    public void testNoHoldBeforeAnyOpponentCreature() {
        Game game = ourTurn(2, false, "Doom Blade", "Child of Night");
        assertEquals(countCardsWithName(game, "Child of Night", ZoneType.Battlefield), 1, gameStateToString(game));
    }
}
