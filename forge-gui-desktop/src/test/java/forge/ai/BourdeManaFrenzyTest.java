package forge.ai;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.testng.annotations.Test;

import forge.game.Game;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

// magic-tools: releve des bourdes v23 (01/11) : « mana » comptait les permanents en main sous Experimental Frenzy
// (rien ne se lance depuis la main) ; le guetteur mana lit maintenant l'interdit depuis la main comme le guetteur removal
public class BourdeManaFrenzyTest extends AITest {

    private String manaLine(boolean frenzy) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        addCard("Mountain", ai);
        if (frenzy) {
            addCard("Experimental Frenzy", ai);
        }
        addCardToZone("Mons's Goblin Raiders", ai, ZoneType.Hand);
        BourdeWatcher w = new BourdeWatcher(game);
        PrintStream old = System.out;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out, true));
        try {
            w.mana(ai);
        } finally {
            System.setOut(old);
        }
        return out.toString();
    }

    @Test
    public void testPermanentLeftInHandCounted() {
        assertTrue(manaLine(false).contains("BOURDE|mana"));
    }

    @Test
    public void testFrenzyNotCounted() {
        assertFalse(manaLine(true).contains("BOURDE|mana"));
    }
}
