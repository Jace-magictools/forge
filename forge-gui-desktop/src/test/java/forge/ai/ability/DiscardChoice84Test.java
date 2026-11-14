package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 84 (a tester reports 29/10 15:46 and 15:55, Gwyll - Vampire turn 5): Humiliate is cast before the
// attack, and takes the removal that would kill our creature rather than Vampire Nighthawk
public class DiscardChoice84Test extends AITest {

    private Game setup(String removal) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        addCard("Plains", ai);
        addCard("Swamp", ai);
        Card angel = addCard("Serra Angel", ai);
        angel.setSickness(false);
        addCardToZone("Humiliate", ai, ZoneType.Hand);
        addCardToZone("Vampire Nighthawk", opponent, ZoneType.Hand);
        addCardToZone(removal, opponent, ZoneType.Hand);
        addCardToZone("Swamp", opponent, ZoneType.Hand);
        // no untapped mana for the opponent: no Cast Down in response
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        ai.setLandsPlayedThisTurn(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        return game;
    }

    @Test
    public void testHumiliateBeforeAttackTakesCastDown() {
        Game game = setup("Cast Down");
        playUntilPhase(game, PhaseType.COMBAT_DECLARE_ATTACKERS);
        assertEquals(countCardsWithName(game, "Humiliate", ZoneType.Graveyard), 1, gameStateToString(game));
        assertEquals(countCardsWithName(game, "Cast Down", ZoneType.Graveyard), 1, gameStateToString(game));
        assertEquals(countCardsWithName(game, "Vampire Nighthawk", ZoneType.Hand), 1, gameStateToString(game));
    }

    @Test
    public void testTakesKickerEdictWhenAlone() {
        Game game = setup("Gatekeeper of Malakir");
        playUntilPhase(game, PhaseType.COMBAT_DECLARE_ATTACKERS);
        assertEquals(countCardsWithName(game, "Gatekeeper of Malakir", ZoneType.Graveyard), 1, gameStateToString(game));
    }

    @Test
    public void testNoAttackerStillMain2() {
        Game game = setup("Cast Down");
        for (Card c : game.getPlayers().get(1).getCreaturesInPlay()) {
            c.setSickness(true);
        }
        playUntilPhase(game, PhaseType.COMBAT_DECLARE_ATTACKERS);
        assertEquals(countCardsWithName(game, "Humiliate", ZoneType.Hand), 1, gameStateToString(game));
    }
}
