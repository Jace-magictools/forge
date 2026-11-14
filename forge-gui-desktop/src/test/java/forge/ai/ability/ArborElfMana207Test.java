package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 207, tester 05/11 (a test game): Arbor Elf is a mana source. It untaps a Forest in main 1 instead of
// being tapped by Birchlore Rangers, which keeps the Elves that make nothing else.
public class ArborElfMana207Test extends AITest {

    private void play(Game game, Player ai) {
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        addCard("Swamp", opponent);
        ai.setLandsPlayedThisTurn(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
    }

    /** the game's turn 2: Birchlore + Fyndhorn with the Forests, Arbor untaps one for Symbiote, the sick Elves pay the rest: five mana, four if Birchlore taps Arbor */
    @Test
    public void testArborPaysBeforeBirchlore() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        addCard("Forest", ai);
        addCard("Forest", ai);
        addCard("Arbor Elf", ai).setSickness(false);
        addCardToZone("Birchlore Rangers", ai, ZoneType.Hand);
        addCardToZone("Fyndhorn Elves", ai, ZoneType.Hand);
        addCardToZone("Wirewood Symbiote", ai, ZoneType.Hand);
        addCardToZone("Elvish Mystic", ai, ZoneType.Hand);
        addCardToZone("Nettle Sentinel", ai, ZoneType.Hand);
        play(game, ai);
        assertEquals(ai.getCardsIn(ZoneType.Hand).size(), 0, gameStateToString(game));
    }

    /** Birchlore taps itself and the sick Elf, not Arbor Elf: two non-Elf spells instead of one */
    @Test
    public void testBirchloreTapsTheSickFirst() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        addCard("Forest", ai).setTapped(true);
        addCard("Birchlore Rangers", ai).setSickness(true);
        addCard("Arbor Elf", ai).setSickness(false);
        addCard("Llanowar Elves", ai).setSickness(true);
        addCardToZone("Experiment One", ai, ZoneType.Hand);
        addCardToZone("Experiment One", ai, ZoneType.Hand);
        play(game, ai);
        assertEquals(ai.getCardsIn(ZoneType.Hand).size(), 0, gameStateToString(game));
    }
}
