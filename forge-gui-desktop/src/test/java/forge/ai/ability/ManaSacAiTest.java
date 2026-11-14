package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertTrue;

public class ManaSacAiTest extends AITest {

    // magic-tools rule 59: a real creature is not sacrificed to Phyrexian Altar for a spell that is not clearly bigger
    // (Smeagol, v8 tournament: Sméagol, Helpful Guide fed to the Altar for the last mana of Insidious Roots).
    private Game altar(String creature, String spell, String land, int lands) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");

        for (int i = 0; i < lands; i++) {
            addCard(land, ai);
        }
        addCard("Phyrexian Altar", ai);
        Card c = addCard(creature, ai);
        c.setSickness(false);
        addCardToZone(spell, ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);

        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai, 5);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        return game;
    }

    private static boolean has(Player p, ZoneType zone, String name) {
        return p.getCardsIn(zone).anyMatch(c -> c.getName().equals(name));
    }

    @Test(invocationCount = 4)
    public void testSmeagolNotSacrificedForInsidiousRoots() {
        Game game = altar("Sméagol, Helpful Guide", "Insidious Roots", "Forest", 1);
        Player ai = game.getPlayers().get(1);
        assertTrue(has(ai, ZoneType.Battlefield, "Sméagol, Helpful Guide"),
                "Sméagol sacrificed for mana\n" + gameStateToString(game));
    }

    // a card that comes back from the graveyard still pays (fodder)
    @Test
    public void testBloodghastPaysForInsidiousRoots() {
        Game game = altar("Bloodghast", "Insidious Roots", "Forest", 1);
        Player ai = game.getPlayers().get(1);
        assertTrue(has(ai, ZoneType.Battlefield, "Insidious Roots"),
                "Insidious Roots not cast with Bloodghast as fodder\n" + gameStateToString(game));
    }

    // a much bigger spell is worth the creature
    @Test
    public void testBearsPayForCrawWurm() {
        Game game = altar("Grizzly Bears", "Craw Wurm", "Forest", 5);
        Player ai = game.getPlayers().get(1);
        assertTrue(has(ai, ZoneType.Battlefield, "Craw Wurm"),
                "Craw Wurm not cast\n" + gameStateToString(game));
    }
}
