package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertTrue;

public class ReanimatorTutorAiTest extends AITest {

    private Game setUp(String spell, int swamps, String... hand) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        for (int i = 0; i < swamps; i++) {
            addCard("Swamp", ai);
        }
        addCardToZone(spell, ai, ZoneType.Hand);
        for (String h : hand) {
            addCardToZone(h, ai, ZoneType.Hand);
        }
        for (String l : new String[] {"Griselbrand", "Akroma, Angel of Wrath", "Animate Dead", "Reanimate", "Dark Ritual", "Demonic Tutor", "Condemn", "Sol Ring", "Entomb"}) {
            addCardToZone(l, ai, ZoneType.Library);
        }
        for (int i = 0; i < 8; i++) {
            addCardToZone("Swamp", ai, ZoneType.Library);
            addCardToZone("Plains", ai, ZoneType.Library);
        }
        fillLibrary(opponent, 20);
        moveToMain2(game, ai);
        return game;
    }

    private static String zones(Player ai) {
        return "graveyard=" + ai.getCardsIn(ZoneType.Graveyard) + " hand=" + ai.getCardsIn(ZoneType.Hand);
    }

    @Test
    public void testEntombPutsFattyInGraveyard() {
        Game game = setUp("Entomb", 1);
        Player ai = game.getPlayers().get(1);
        gameLoopUntilNextPhase(game);
        // Akroma or Griselbrand: both are worth reanimating
        assertTrue(ai.getCardsIn(ZoneType.Graveyard).anyMatch(c -> c.isCreature() && c.getCMC() >= 6), zones(ai));
    }

    @Test
    public void testFinalPartingBuriesFattyAndTutorsReanimation() {
        Game game = setUp("Final Parting", 5);
        Player ai = game.getPlayers().get(1);
        gameLoopUntilNextPhase(game);
        assertTrue(ai.getCardsIn(ZoneType.Graveyard).anyMatch(c -> c.isCreature() && c.getCMC() >= 6), zones(ai));
        assertTrue(ai.getCardsIn(ZoneType.Hand).anyMatch(c -> c.getName().equals("Animate Dead")), zones(ai));
    }

    private static boolean holdsReanimation(Player ai) {
        return ai.getCardsIn(ZoneType.Hand).anyMatch(c -> c.getName().equals("Animate Dead") || c.getName().equals("Reanimate"));
    }

    @Test
    public void testDemonicTutorFindsReanimationWhenFattyInGraveyard() {
        Game game = setUp("Demonic Tutor", 2);
        Player ai = game.getPlayers().get(1);
        addCardToZone("Iona, Shield of Emeria", ai, ZoneType.Graveyard);
        gameLoopUntilNextPhase(game);
        assertTrue(holdsReanimation(ai), zones(ai));
    }

    @Test
    public void testMausoleumSecretsFindsReanimateNotRitual() {
        // one creature in the graveyard: Reanimate and Dark Ritual (mana value 1) are the only black choices
        Game game = setUp("Mausoleum Secrets", 2);
        Player ai = game.getPlayers().get(1);
        addCardToZone("Iona, Shield of Emeria", ai, ZoneType.Graveyard);
        gameLoopUntilNextPhase(game);
        assertTrue(holdsReanimation(ai), zones(ai));
    }

    // magic-tools: Profanation, Demonic Tutor took Avacyn into hand next to Reanimate with an empty graveyard
    @Test
    public void testDemonicTutorFindsEntombWhenReanimationInHand() {
        Game game = setUp("Demonic Tutor", 2, "Reanimate");
        Player ai = game.getPlayers().get(1);
        gameLoopUntilNextPhase(game);
        assertTrue(ai.getCardsIn(ZoneType.Hand).anyMatch(c -> c.getName().equals("Entomb"))
                || ai.getCardsIn(ZoneType.Graveyard).anyMatch(c -> c.isCreature() && c.getCMC() >= 6), zones(ai));
    }

    // magic-tools: rule 19, Profanation held Akroma and Sparkhunter Masticore for six turns (discard cost refused)
    @Test
    public void testMasticoreDiscardsFattyForReanimation() {
        Game game = setUp("Sparkhunter Masticore", 3, "Akroma, Angel of Wrath");
        Player ai = game.getPlayers().get(1);
        gameLoopUntilNextPhase(game);
        assertTrue(ai.getCardsIn(ZoneType.Graveyard).anyMatch(c -> c.getName().equals("Akroma, Angel of Wrath")), zones(ai));
    }

    @Test
    public void testMasticoreKeepsFattyWithoutReanimation() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        for (int i = 0; i < 3; i++) {
            addCard("Swamp", ai);
        }
        addCardToZone("Sparkhunter Masticore", ai, ZoneType.Hand);
        addCardToZone("Akroma, Angel of Wrath", ai, ZoneType.Hand);
        for (int i = 0; i < 10; i++) {
            addCardToZone("Swamp", ai, ZoneType.Library);
        }
        fillLibrary(game.getPlayers().get(0), 20);
        moveToMain2(game, ai);
        gameLoopUntilNextPhase(game);
        assertTrue(ai.getCardsIn(ZoneType.Hand).anyMatch(c -> c.getName().equals("Akroma, Angel of Wrath")), zones(ai));
    }

    // magic-tools: rule 19b, Profanation game 9 (prof26): 5 lands, Sanctuary Warden castable next turn, Exhume in hand
    @Test
    public void testMasticoreDiscardsFattyCastableNextTurnWithSpellInHand() {
        Game game = setUp("Sparkhunter Masticore", 5, "Sanctuary Warden", "Exhume");
        Player ai = game.getPlayers().get(1);
        gameLoopUntilNextPhase(game);
        assertTrue(!ai.getCardsIn(ZoneType.Hand).anyMatch(c -> c.getName().equals("Sanctuary Warden")), zones(ai));
    }

    @Test
    public void testMasticoreKeepsFattyCastableNextTurnWithoutSpellInHand() {
        Game game = setUp("Sparkhunter Masticore", 5, "Sanctuary Warden");
        Player ai = game.getPlayers().get(1);
        gameLoopUntilNextPhase(game);
        assertTrue(ai.getCardsIn(ZoneType.Hand).anyMatch(c -> c.getName().equals("Sanctuary Warden")), zones(ai));
    }
}
