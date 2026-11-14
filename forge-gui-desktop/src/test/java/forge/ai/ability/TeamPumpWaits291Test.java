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
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.assertFalse;

// magic-tools rule 291 (plateau, Ugin bdb5892cd; tester 11/11: Orc drums pumped the team in main 1, then no attack):
// an instant-speed team pump until end of turn waits for combat, like a single-creature pump already does.
public class TeamPumpWaits291Test extends AITest {

    private SpellAbility setup(Game game, Player ai, String spell) {
        Player opponent = game.getPlayers().get(0);
        addCard("Hill Giant", opponent);
        for (int i = 0; i < 2; i++) {
            addCard("Grizzly Bears", ai).setSickness(false);
        }
        for (int i = 0; i < 4; i++) {
            addCard("Plains", ai);
            addCard("Mountain", ai);
        }
        Card c = addCardToZone(spell, ai, ZoneType.Hand);
        SpellAbility sa = c.getSpellAbilities().get(0);
        sa.setActivatingPlayer(ai);
        return sa;
    }

    @Test
    public void testInstantTeamPumpWaitsInMain1() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        SpellAbility sa = setup(game, ai, "Rally the Peasants");
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        assertTrue(PumpAllAi.waitsForCombat(ai, sa));
        assertFalse(new PumpAllAi().checkPhaseRestrictions(ai, sa, game.getPhaseHandler()), "team pump in main 1");
        AiPlayDecision d = ((PlayerControllerAi) ai.getController()).getAi().canPlaySa(sa);
        assertNotEquals(d, AiPlayDecision.WillPlay, "Rally the Peasants cast before attacks");
    }

    @Test
    public void testSorceryTeamPumpStillInMain1() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        SpellAbility sa = setup(game, ai, "Trumpet Blast");
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_BLOCKERS, ai);
        assertTrue(new PumpAllAi().checkPhaseRestrictions(ai, sa, game.getPhaseHandler()), "team pump allowed in combat");
        SpellAbility sorcery = setup(game, ai, "Overrun");
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        assertFalse(PumpAllAi.waitsForCombat(ai, sorcery), "sorcery speed is for main 1");
    }
}
