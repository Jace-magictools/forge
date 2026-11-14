package forge.ai.ability;

import org.testng.annotations.Test;

import com.google.common.collect.Lists;

import forge.ai.AITest;
import forge.ai.AiController;
import forge.ai.PlayerControllerAi;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.CardLists;
import forge.game.combat.Combat;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class OdricBlockAiTest extends AITest {

    private Game odricGame(String blocker, int blockers, int opponentLife) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard("Odric, Master Tactician", ai).setSickness(false);
        for (int i = 0; i < 3; i++) {
            addCard("Grizzly Bears", ai).setSickness(false);
        }
        for (int i = 0; i < blockers; i++) {
            addCard(blocker, opponent);
        }
        opponent.setLife(opponentLife, null);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.MAIN2);
        return game;
    }

    // with Odric and three others attacking, the AI blocks with the opponent's creatures into its attackers
    @Test
    public void testOdricBlocksOpponentCreaturesIntoDeath() {
        Game game = odricGame("Llanowar Elves", 2, 20);
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        assertEquals(CardLists.count(opponent.getCardsIn(ZoneType.Graveyard), c -> c.getName().equals("Llanowar Elves")), 2,
                "elves not blocked into the attackers\n" + gameStateToString(game));
        assertEquals(ai.getCreaturesInPlay().size(), 4, "an attacker died\n" + gameStateToString(game));
    }

    // nobody blocks when the unblocked attack is lethal
    @Test
    public void testOdricLetsEverythingThroughWhenLethal() {
        Game game = odricGame("Llanowar Elves", 2, 9);
        Player opponent = game.getPlayers().get(0);
        assertTrue(game.isGameOver() || opponent.getLife() <= 0, "opponent survived a lethal attack\n" + gameStateToString(game));
    }

    private Combat odricCombat(boolean aiDeclaresBlockers) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        List<Card> attackers = Lists.newArrayList(addCard("Odric, Master Tactician", ai));
        for (int i = 0; i < 3; i++) {
            attackers.add(addCard("Grizzly Bears", ai));
        }
        addCard("Craw Wurm", opponent);
        addCard("Llanowar Elves", opponent);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        if (aiDeclaresBlockers) {
            opponent.addDeclaresBlockers(game.getNextTimestamp(), ai);
        }
        AiController brains = ((PlayerControllerAi) ai.getController()).getAi();
        Combat combat = brains.getPredictedCombat();
        for (Card a : attackers) {
            combat.addAttacker(a, opponent);
        }
        brains.declareBlockersFor(opponent, combat);
        return combat;
    }

    // a blocker that would kill one of the attackers does not block, the one that dies does
    @Test
    public void testOdricDoesNotBlockWithKillers() {
        Combat combat = odricCombat(true);
        Game game = combat.getAttackingPlayer().getGame();
        Card wurm = findCardWithName(game, "Craw Wurm");
        Card elves = findCardWithName(game, "Llanowar Elves");
        assertFalse(combat.isBlocking(wurm), "Craw Wurm blocks one of our attackers");
        assertTrue(combat.isBlocking(elves), "Llanowar Elves not blocked into an attacker");
    }

    // without Odric's effect, predicting the opponent's blocks keeps the defender's own logic
    @Test
    public void testPredictionKeepsDefenderBlocks() {
        Combat combat = odricCombat(false);
        Card wurm = findCardWithName(combat.getAttackingPlayer().getGame(), "Craw Wurm");
        assertTrue(combat.isBlocking(wurm), "predicted blocks: the defender should block with its Craw Wurm");
    }
}
