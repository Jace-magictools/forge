package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.CounterEnumType;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

// magic-tools rule 321 (a tester, 17/11, measure on 1 200 arbiter logs: 18 positions where the AI let an opponent's
// spell take a creature it could have sacrificed in response). Kitchen Finks under Swords to Plowshares with
// Viscera Seer on the battlefield: sacrifice it in response, persist brings it back.
public class SacInResponse321Test extends AITest {

    private Game run(String spell, boolean onFinks, String outlet, boolean melira) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        Card finks = addCard("Kitchen Finks", ai);
        finks.setSickness(false);
        addCard(outlet, ai).setSickness(false);
        if (melira) {
            addCard("Melira, Sylvok Outcast", ai).setSickness(false);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 30);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, opponent);
        game.getAction().checkStateEffects(true);

        Card c = addCardToZone(spell, opponent, ZoneType.Hand);
        SpellAbility sa = c.getFirstSpellAbility();
        sa.setActivatingPlayer(opponent);
        if (sa.usesTargeting()) {
            if (onFinks) {
                sa.getTargets().add(finks);
            } else {
                sa.getTargets().add(ai);
            }
        }
        game.getStackZone().add(c);
        game.getStack().add(sa);
        playUntilStackClear(game);
        return game;
    }

    private static Card finks(Game game) {
        return game.getPlayers().get(1).getCardsIn(ZoneType.Battlefield).stream()
                .filter(c -> c.getName().equals("Kitchen Finks")).findFirst().orElse(null);
    }

    // Swords on Finks: sacrificed in response, back with a -1/-1 counter instead of exiled
    @Test(timeOut = 120000)
    public void testSacrificesExiledPersistCreature() {
        Game game = run("Swords to Plowshares", true, "Viscera Seer", false);
        Card f = finks(game);
        assertTrue(f != null && f.getCounters(CounterEnumType.M1M1) == 1, gameStateToString(game));
    }

    // Bolt to the face: Finks is not threatened, nothing is sacrificed
    @Test(timeOut = 120000)
    public void testNoSacrificeWithoutThreat() {
        Game game = run("Lightning Bolt", false, "Viscera Seer", false);
        Card f = finks(game);
        assertTrue(f != null && f.getCounters(CounterEnumType.M1M1) == 0, gameStateToString(game));
    }

    // Wrath of God against Melira + Finks + Altar of Dementia: the loop runs in response and mills the opponent out
    @Test(timeOut = 120000)
    public void testLoopRunsInResponseToWrath() {
        Game game = run("Wrath of God", true, "Altar of Dementia", true);
        Player opp = game.getPlayers().get(0);
        assertTrue(game.isGameOver() || opp.getCardsIn(ZoneType.Library).isEmpty(),
                "library " + opp.getCardsIn(ZoneType.Library).size() + "\n" + gameStateToString(game));
        assertEquals(game.isGameOver(), game.isGameOver());
    }
}
