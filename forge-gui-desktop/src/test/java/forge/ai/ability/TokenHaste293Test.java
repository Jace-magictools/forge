package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.ai.AiPlayDecision;
import forge.ai.PlayerControllerAi;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

// magic-tools rule 293 (Sauron deck, 11/11): Song of Totentanz makes rats that can't block and gives them haste:
// cast after combat, the rats never attack that turn. Haste granted by the spell's own sub-ability counts as haste.
public class TokenHaste293Test extends AITest {

    private SpellAbility spell(Game game, Player ai, String name) {
        Card c = addCardToZone(name, ai, forge.game.zone.ZoneType.Hand);
        for (int i = 0; i < 5; i++) {
            addCard("Mountain", ai);
        }
        SpellAbility sa = c.getFirstSpellAbility();
        sa.setActivatingPlayer(ai);
        return sa;
    }

    @Test
    public void testSongCastBeforeCombat() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        SpellAbility sa = spell(game, ai, "Song of Totentanz");
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        assertTrue(TokenAi.grantsHaste293(sa));
        AiPlayDecision d = ((PlayerControllerAi) ai.getController()).getAi().canPlaySa(sa);
        assertEquals(d, AiPlayDecision.WillPlay, "Song of Totentanz cast in main 1, the rats attack");
    }

    @Test
    public void testPlainTokensWait() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        SpellAbility sa = spell(game, ai, "Dragon Fodder");
        assertFalse(TokenAi.grantsHaste293(sa), "no haste given");
    }
}
