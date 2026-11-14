package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertTrue;

public class ReanimateAiTest extends AITest {

    // Griselbrand in our graveyard, two Swamps, a reanimation spell in hand: the AI should bring it back
    private void checkReanimates(String spell) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard("Swamp", ai);
        addCard("Swamp", ai);
        addCard("Swamp", ai);
        addCardToZone("Griselbrand", ai, ZoneType.Graveyard);
        addCardToZone(spell, ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        moveToMain2(game, ai);
        gameLoopUntilNextPhase(game);
        assertTrue(ai.getCreaturesInPlay().anyMatch(c -> c.getName().equals("Griselbrand")), spell + " did not reanimate Griselbrand");
    }

    @Test
    public void testAnimateDead() { checkReanimates("Animate Dead"); }

    @Test
    public void testReanimate() { checkReanimates("Reanimate"); }

    @Test
    public void testExhume() { checkReanimates("Exhume"); }

    @Test
    public void testNecromancy() { checkReanimates("Necromancy"); }
}
