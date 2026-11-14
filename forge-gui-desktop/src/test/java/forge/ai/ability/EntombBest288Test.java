package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 288 (11/11, Profanation, under-rated by Forge): Entomb took the deck's first key card (a 2nd Entomb,
// Animate Dead) to the graveyard instead of the creature to reanimate
public class EntombBest288Test extends AITest {

    @Test(timeOut = 120000)
    public void testEntombBuriesTheFatty() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        Deck d = ai.getRegisteredPlayer().getDeck();
        d.addKeyCard("Entomb");
        d.addKeyCard("Animate Dead");
        addCard("Swamp", ai);
        addCard("Swamp", ai);
        addCardToZone("Entomb", ai, ZoneType.Hand);
        addCardToZone("Entomb", ai, ZoneType.Library);
        addCardToZone("Animate Dead", ai, ZoneType.Library);
        addCardToZone("Griselbrand", ai, ZoneType.Library);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        playUntilNextTurn(game);
        String grave = ai.getCardsIn(ZoneType.Graveyard).toString();
        assertEquals(countCardsWithName(game, "Griselbrand", ZoneType.Graveyard), 1, "graveyard: " + grave);
    }
}
