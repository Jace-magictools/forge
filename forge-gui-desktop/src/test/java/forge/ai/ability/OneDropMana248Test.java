package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 248, D142 (tester 08/11, a test game T1): one Island, Sensei's Divining Top stayed in
// hand, the mana was lost.
public class OneDropMana248Test extends AITest {

    private Game setup(String... hand) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        addCard("Island", ai);
        for (String c : hand) {
            addCardToZone(c, ai, ZoneType.Hand);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        ai.setLandsPlayedThisTurn(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        return game;
    }

    /** the report's hand */
    @Test
    public void testTopOnTurnOne() {
        Game game = setup("Sensei's Divining Top", "Darksteel Ingot", "Spell Burst", "Urza's Power Plant",
                "Trinket Mage", "Counterlash");
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Sensei's Divining Top", ZoneType.Battlefield), 1, gameStateToString(game));
    }

    /** the Top alone */
    @Test
    public void testTopAlone() {
        Game game = setup("Sensei's Divining Top");
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Sensei's Divining Top", ZoneType.Battlefield), 1, gameStateToString(game));
    }

    /** Top with Spell Burst only */
    @Test
    public void testTopWithSpellBurst() {
        Game game = setup("Sensei's Divining Top", "Spell Burst");
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Sensei's Divining Top", ZoneType.Battlefield), 1, gameStateToString(game));
    }
}
