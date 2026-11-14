package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 79 (a tester report 29/10 12:16, Dauthi): Vault Skirge ({1}{B/P}) is paid with 2 life when the mana
// saved casts another spell this turn; with nothing else to cast, or at low life, the mana pays
public class PhyrexianSpare79Test extends AITest {

    private Game setup(int swamps, int life, String... hand) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        for (int i = 0; i < swamps; i++) {
            addCard("Swamp", ai);
        }
        for (String h : hand) {
            addCardToZone(h, ai, ZoneType.Hand);
        }
        ai.setLife(life, null);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        ai.setLandsPlayedThisTurn(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        return game;
    }

    @Test
    public void testLifePaysWhenItCastsAnotherSpell() {
        Game game = setup(2, 20, "Vault Skirge", "Bonesplitter");
        Player ai = game.getPlayers().get(1);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Vault Skirge", ZoneType.Battlefield), 1, gameStateToString(game));
        assertEquals(countCardsWithName(game, "Bonesplitter", ZoneType.Battlefield), 1, gameStateToString(game));
        assertEquals(ai.getLife(), 18);
    }

    @Test
    public void testManaPaysWhenNothingElse() {
        Game game = setup(2, 20, "Vault Skirge");
        Player ai = game.getPlayers().get(1);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Vault Skirge", ZoneType.Battlefield), 1, gameStateToString(game));
        assertEquals(ai.getLife(), 20);
    }

    @Test
    public void testManaPaysAtLowLife() {
        Game game = setup(2, 9, "Vault Skirge", "Bonesplitter");
        Player ai = game.getPlayers().get(1);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Vault Skirge", ZoneType.Battlefield), 1, gameStateToString(game));
        assertEquals(ai.getLife(), 9);
    }
}
