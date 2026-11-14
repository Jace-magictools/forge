package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertTrue;

// magic-tools: rule 182, -1/-1 counters on a target are a curse even without IsCurse$ True (Lame de Morgul, D104)
public class HostileCounters182Test extends AITest {

    private Player setup(Game game, boolean oppBear) {
        Player opp = game.getPlayers().get(0);
        Player ai = game.getPlayers().get(1);
        for (int i = 0; i < 5; i++) {
            addCard("Swamp", ai);
        }
        Card spell = addCardToZone("Torment of Venom", ai, ZoneType.Hand);
        for (SpellAbility sa : spell.getSpellAbilities()) {
            sa.removeParam("IsCurse");
        }
        addCard("Hill Giant", ai).setSickness(true);
        if (oppBear) {
            addCard("Grizzly Bears", opp).setSickness(false);
        }
        fillLibrary(ai, 20);
        fillLibrary(opp, 20);
        ai.addLandPlayedThisTurn();
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        playUntilNextTurn(game);
        return ai;
    }

    // no opposing creature: the spell stays in hand, our Hill Giant lives
    @Test(timeOut = 120000)
    public void testNotOnOwnCreature() {
        Game game = initAndCreateGame();
        Player ai = setup(game, false);
        assertTrue(ai.getCardsIn(ZoneType.Battlefield).anyMatch(c -> c.getName().equals("Hill Giant")),
                "log=" + game.getGameLog().getLogEntries(null));
    }

    // an opposing creature: it is the one that gets the counters
    @Test(timeOut = 120000)
    public void testOnOpposingCreature() {
        Game game = initAndCreateGame();
        Player ai = setup(game, true);
        Player opp = game.getPlayers().get(0);
        assertTrue(opp.getCardsIn(ZoneType.Graveyard).anyMatch(c -> c.getName().equals("Grizzly Bears")),
                "log=" + game.getGameLog().getLogEntries(null));
        assertTrue(ai.getCardsIn(ZoneType.Battlefield).anyMatch(c -> c.getName().equals("Hill Giant")), "own");
    }
}
