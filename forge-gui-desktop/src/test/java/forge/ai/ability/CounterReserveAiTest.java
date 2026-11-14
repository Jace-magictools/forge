package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 22, keep the mana of a counterspell for the opponent's turn
// (Transfuge cast its cheap creatures on its turn and had nothing left to counter with)
public class CounterReserveAiTest extends AITest {

    private Game ourTurn(int islands, String... hand) {
        return ourTurn(islands, 0, hand);
    }

    private Game ourTurn(int islands, int forests, String... hand) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        for (int i = 0; i < islands; i++) {
            addCard("Island", ai);
        }
        for (int i = 0; i < forests; i++) {
            addCard("Forest", ai);
        }
        addCard("Grizzly Bears", opponent);
        for (String c : hand) {
            addCardToZone(c, ai, ZoneType.Hand);
        }
        // rule 174: a counterspell deck, not twenty bears (an aggressive deck deploys first)
        for (int i = 0; i < 20; i++) {
            addCardToZone("Island", ai, ZoneType.Library);
        }
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        return game;
    }

    @Test
    public void testKeepsCounterspellMana() {
        Game game = ourTurn(3, "Counterspell", "Coral Eel");
        assertEquals(countCardsWithName(game, "Coral Eel", ZoneType.Battlefield), 0, gameStateToString(game));
    }

    // enough mana for both: cast the creature, keep the counterspell up
    @Test
    public void testCastsWhenCounterStaysPayable() {
        Game game = ourTurn(4, "Counterspell", "Coral Eel");
        assertEquals(countCardsWithName(game, "Coral Eel", ZoneType.Battlefield), 1, gameStateToString(game));
    }

    // a big play is worth tapping out for (Control Magic, Agent of Treachery, Thassa)
    @Test
    public void testTapsOutForControlMagic() {
        Game game = ourTurn(4, "Counterspell", "Control Magic");
        assertEquals(countCardsWithName(game, "Control Magic", ZoneType.Battlefield), 1, gameStateToString(game));
    }

    // one blue source: Counterspell (U U) cannot be cast anyway, nothing to keep the mana for (grenouille, 26/10)
    @Test
    public void testNoHoldWhenCounterColorsMissing() {
        Game game = ourTurn(1, 2, "Counterspell", "Coral Eel");
        assertEquals(countCardsWithName(game, "Coral Eel", ZoneType.Battlefield), 1, gameStateToString(game));
    }
}
