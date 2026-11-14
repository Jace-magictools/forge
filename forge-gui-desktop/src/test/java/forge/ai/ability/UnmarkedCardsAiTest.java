package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.CounterEnumType;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/** Cards played by our decks that Forge marks AI:RemoveDeck:All (scripts/forge/cards/ removes the marker). */
public class UnmarkedCardsAiTest extends AITest {

    private Game reanimatorHand(boolean withReanimation) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard("Swamp", ai);
        addCard("Swamp", ai);
        addCardToZone("Sickening Dreams", ai, ZoneType.Hand);
        addCardToZone("Griselbrand", ai, ZoneType.Hand);
        addCardToZone("Plains", ai, ZoneType.Hand);
        if (withReanimation) {
            addCardToZone("Animate Dead", ai, ZoneType.Hand);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        moveToMain2(game, ai);
        return game;
    }

    // Profanation: Sickening Dreams for X = 1 puts Griselbrand in the graveyard for Animate Dead
    @Test
    public void testSickeningDreamsDiscardsFattyForReanimation() {
        Game game = reanimatorHand(true);
        Player ai = game.getPlayers().get(1);
        gameLoopUntilNextPhase(game);
        assertTrue(ai.getCardsIn(ZoneType.Graveyard).anyMatch(c -> c.getName().equals("Griselbrand")), "Griselbrand not discarded");
        assertTrue(ai.getCardsIn(ZoneType.Hand).anyMatch(c -> c.getName().equals("Animate Dead")), "Animate Dead discarded");
        assertEquals(ai.getLife(), 19);
    }

    @Test
    public void testSickeningDreamsKeptWithoutReanimation() {
        Game game = reanimatorHand(false);
        Player ai = game.getPlayers().get(1);
        gameLoopUntilNextPhase(game);
        assertTrue(ai.getCardsIn(ZoneType.Hand).anyMatch(c -> c.getName().equals("Sickening Dreams")), "Sickening Dreams cast for nothing");
    }

    // Burn: Reckless Abandon sacrifices Goblin Arsonist for the last 4 points (and the Arsonist's 1)
    @Test
    public void testRecklessAbandonFinishesOpponent() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard("Mountain", ai);
        addCard("Goblin Arsonist", ai).setSickness(false);
        addCardToZone("Reckless Abandon", ai, ZoneType.Hand);
        opponent.setLife(4, null);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        moveToMain2(game, ai);
        gameLoopUntilNextPhase(game);
        assertTrue(opponent.getLife() <= 0 || game.isGameOver(), "Reckless Abandon not cast for lethal");
    }

    // Profanation: Vampiric Tutor puts the reanimation spell on top when Griselbrand is already in the graveyard
    @Test
    public void testVampiricTutorFindsReanimation() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard("Swamp", ai);
        addCardToZone("Griselbrand", ai, ZoneType.Graveyard);
        addCardToZone("Vampiric Tutor", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        addCardToZone("Animate Dead", ai, ZoneType.Library);
        fillLibrary(opponent, 20);
        // rule 107: cast at the opponent's end step
        moveToMain2(game, opponent);
        playUntilPhase(game, forge.game.phase.PhaseType.CLEANUP);
        assertTrue(ai.getCardsIn(ZoneType.Graveyard).anyMatch(c -> c.getName().equals("Vampiric Tutor")), "Vampiric Tutor not cast");
        assertEquals(ai.getCardsIn(ZoneType.Library).getFirst().getName(), "Animate Dead");
    }

    // Giga: Wild Growth goes on the AI's own land
    @Test
    public void testWildGrowthOnOwnLand() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard("Forest", ai);
        addCard("Forest", opponent);
        addCardToZone("Wild Growth", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        moveToMain2(game, ai);
        gameLoopUntilNextPhase(game);
        assertTrue(ai.getCardsIn(ZoneType.Battlefield).anyMatch(c -> c.getName().equals("Wild Growth") && c.isAttachedToEntity()
                && c.getEntityAttachedTo() instanceof forge.game.card.Card l && l.getController().equals(ai)), "Wild Growth not on the AI's land");
    }

    // Poudlar: Magmatic Insight discards a spare land, never the only one in hand when lands are short
    @Test
    public void testMagmaticInsightWithSpareLand() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard("Mountain", ai);
        addCard("Mountain", ai);
        addCard("Mountain", ai);
        addCardToZone("Magmatic Insight", ai, ZoneType.Hand);
        addCardToZone("Mountain", ai, ZoneType.Hand);
        addCardToZone("Mountain", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        moveToMain2(game, ai);
        gameLoopUntilNextPhase(game);
        assertTrue(ai.getCardsIn(ZoneType.Graveyard).anyMatch(c -> c.getName().equals("Magmatic Insight")), "Magmatic Insight not cast");
    }

    @Test
    public void testMagmaticInsightKeepsNeededLand() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard("Mountain", ai);
        addCardToZone("Magmatic Insight", ai, ZoneType.Hand);
        addCardToZone("Mountain", ai, ZoneType.Hand);
        addCardToZone("Hill Giant", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        moveToMain2(game, ai);
        gameLoopUntilNextPhase(game);
        assertTrue(ai.getCardsIn(ZoneType.Hand).anyMatch(c -> c.getName().equals("Magmatic Insight")), "Magmatic Insight discarded a needed land");
    }

    // Flying Blue: Skyship Plunderer is cast, and its combat damage trigger grows our own +1/+1 counters
    @Test
    public void testSkyshipPlundererCast() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard("Island", ai);
        addCard("Island", ai);
        addCardToZone("Skyship Plunderer", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        moveToMain2(game, ai);
        gameLoopUntilNextPhase(game);
        assertTrue(ai.getCardsIn(ZoneType.Battlefield).anyMatch(c -> c.getName().equals("Skyship Plunderer")), "Skyship Plunderer not cast");
    }

    @Test
    public void testSkyshipPlundererGrowsOwnCounters() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard("Skyship Plunderer", ai).setSickness(false);
        Card raptor = addCard("Cloudfin Raptor", ai);
        raptor.addCounterInternal(CounterEnumType.P1P1, 2, ai, false, null, null);
        Card oppBear = addCard("Grizzly Bears", opponent);
        oppBear.addCounterInternal(CounterEnumType.P1P1, 1, opponent, false, null, null);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.MAIN2);
        assertTrue(opponent.getLife() <= 18, "Skyship Plunderer did not connect");
        assertEquals(raptor.getCounters(CounterEnumType.P1P1), 3);
        assertEquals(oppBear.getCounters(CounterEnumType.P1P1), 1);
    }

    private Game undyingEvilGame(boolean withMurder) {
        return undyingGame("Undying Evil", withMurder);
    }

    private Game undyingGame(String spell, boolean withMurder) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard("Serra Angel", ai);
        addCard("Swamp", ai);
        addCardToZone(spell, ai, ZoneType.Hand);
        for (int i = 0; i < 3; i++) {
            addCard("Swamp", opponent);
        }
        if (withMurder) {
            addCardToZone("Murder", opponent, ZoneType.Hand);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, opponent);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        return game;
    }

    // Karlov, Penitence: Undying Evil answers removal, the creature comes back with a +1/+1 counter
    @Test
    public void testUndyingEvilSavesFromRemoval() {
        Game game = undyingEvilGame(true);
        assertEquals(countCardsWithName(game, "Murder", ZoneType.Graveyard), 1, "Murder not cast\n" + gameStateToString(game));
        Card angel = findCardWithName(game, "Serra Angel");
        assertTrue(angel != null && angel.isInZone(ZoneType.Battlefield) && angel.getCounters(CounterEnumType.P1P1) == 1,
                "Serra Angel not saved\n" + gameStateToString(game));
    }

    @Test
    public void testUndyingEvilKeptWithoutThreat() {
        Game game = undyingEvilGame(false);
        assertEquals(countCardsWithName(game, "Undying Evil", ZoneType.Hand), 1, "Undying Evil cast for nothing");
    }

    // Penitence: the opponent's Pestilence would kill High Priest of Penance, Undying Evil brings it back as a 2/2
    @Test
    public void testUndyingEvilAgainstPestilence() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard("High Priest of Penance", ai);
        addCard("Swamp", ai);
        addCardToZone("Undying Evil", ai, ZoneType.Hand);
        addCard("Pestilence", opponent);
        addCard("Serra Angel", opponent);
        for (int i = 0; i < 3; i++) {
            addCard("Swamp", opponent);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, opponent);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.CLEANUP);
        assertTrue(opponent.getLife() < 20, "Pestilence not activated\n" + gameStateToString(game));
        Card priest = findCardWithName(game, "High Priest of Penance");
        assertTrue(priest != null && priest.getCounters(CounterEnumType.P1P1) == 1,
                "High Priest of Penance not saved\n" + gameStateToString(game));
    }

    // Penitence: Undying Malice answers removal, the creature comes back tapped with a +1/+1 counter
    @Test
    public void testUndyingMaliceSavesFromRemoval() {
        Game game = undyingGame("Undying Malice", true);
        assertEquals(countCardsWithName(game, "Murder", ZoneType.Graveyard), 1, "Murder not cast\n" + gameStateToString(game));
        Card angel = findCardWithName(game, "Serra Angel");
        assertTrue(angel != null && angel.isInZone(ZoneType.Battlefield) && angel.getCounters(CounterEnumType.P1P1) == 1,
                "Serra Angel not saved\n" + gameStateToString(game));
    }

    @Test
    public void testUndyingMaliceKeptWithoutThreat() {
        Game game = undyingGame("Undying Malice", false);
        assertEquals(countCardsWithName(game, "Undying Malice", ZoneType.Hand), 1, "Undying Malice cast for nothing");
    }

    private Game stageGame(boolean withCloudpost) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard("Thespian's Stage", ai);
        addCard(withCloudpost ? "Cloudpost" : "Mountain", ai);
        addCard("Mountain", ai);
        addCard("Mountain", ai);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        moveToMain2(game, ai);
        gameLoopUntilNextPhase(game);
        return game;
    }

    // Ashnod: Thespian's Stage becomes a second Cloudpost (tester: Cloudpost first)
    @Test
    public void testThespiansStageCopiesCloudpost() {
        Game game = stageGame(true);
        assertEquals(countCardsWithName(game, "Cloudpost"), 2, "Stage not copied\n" + gameStateToString(game));
    }

    @Test
    public void testThespiansStageKeptWithoutLocus() {
        Game game = stageGame(false);
        assertEquals(countCardsWithName(game, "Thespian's Stage"), 1, "Stage copied a plain land\n" + gameStateToString(game));
    }

    private Game rudeAwakeningGame(int forests, int oppLife) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        for (int k = 0; k < forests; k++) {
            addCard("Forest", ai).setSickness(false);
        }
        addCardToZone("Rude Awakening", ai, ZoneType.Hand);
        addCard("Grizzly Bears", opponent);
        opponent.setLife(oppLife, null);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.MAIN2);
        return game;
    }

    // Nissa: Rude Awakening entwined at 8 mana, then nine 2/2 lands attack for lethal
    @Test
    public void testRudeAwakeningEntwinedForLethal() {
        Game game = rudeAwakeningGame(9, 10);
        Player opponent = game.getPlayers().get(0);
        assertTrue(game.isGameOver() || opponent.getLife() <= 0, "no lethal land attack\n" + gameStateToString(game));
    }

    @Test
    public void testRudeAwakeningKeptWithoutLethal() {
        Game game = rudeAwakeningGame(9, 20);
        assertEquals(countCardsWithName(game, "Rude Awakening", ZoneType.Hand), 1, "cast without lethal\n" + gameStateToString(game));
    }

    // never one mode alone: 7 lands cannot pay the entwine
    @Test
    public void testRudeAwakeningNeverWithoutEntwine() {
        Game game = rudeAwakeningGame(7, 2);
        assertEquals(countCardsWithName(game, "Rude Awakening", ZoneType.Hand), 1, "cast without entwine\n" + gameStateToString(game));
    }

    private Game cullingScalesInHand(String ownPermanent) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        for (int k = 0; k < 3; k++) {
            addCard("Island", ai);
        }
        addCard(ownPermanent, ai);
        addCardToZone("Culling Scales", ai, ZoneType.Hand);
        addCard("Grizzly Bears", opponent);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        moveToMain2(game, ai);
        gameLoopUntilNextPhase(game);
        return game;
    }

    // Eldratron: Culling Scales next to Darksteel Ingot will kill the opponent's 2-drop
    @Test
    public void testCullingScalesCastAgainstSmallPermanent() {
        Game game = cullingScalesInHand("Darksteel Ingot");
        assertEquals(countCardsWithName(game, "Culling Scales", ZoneType.Battlefield), 1, "Scales not cast\n" + gameStateToString(game));
    }

    @Test
    public void testCullingScalesKeptWhenItKillsOurTop() {
        Game game = cullingScalesInHand("Sensei's Divining Top");
        assertEquals(countCardsWithName(game, "Culling Scales", ZoneType.Hand), 1, "Scales would destroy our Top\n" + gameStateToString(game));
    }

    // tied at mana value 3, the upkeep trigger hits the indestructible Ingot, not the Scales
    @Test
    public void testCullingScalesHitsIndestructible() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard("Island", ai);
        addCard("Culling Scales", ai);
        addCard("Darksteel Ingot", ai);
        addCard("Serra Angel", opponent);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.UNTAP, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.MAIN1);
        assertEquals(countCardsWithName(game, "Culling Scales", ZoneType.Battlefield), 1, "Scales destroyed itself\n" + gameStateToString(game));
        assertEquals(countCardsWithName(game, "Darksteel Ingot", ZoneType.Battlefield), 1);
    }

    // Sauron, Dimir, Penitence: Rakdos Signet turns a Swamp into {B}{R}, Gray Ogre ({2}{R}) castable with two Swamps
    private Game signetBoard(boolean withSpell) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard("Swamp", ai);
        addCard("Swamp", ai);
        addCard("Rakdos Signet", ai);
        if (withSpell) {
            addCardToZone("Gray Ogre", ai, ZoneType.Hand);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        moveToMain2(game, ai);
        return game;
    }

    @Test
    public void testSignetPaysSpell() {
        Game game = signetBoard(true);
        Player ai = game.getPlayers().get(1);
        gameLoopUntilNextPhase(game);
        assertTrue(ai.getCardsIn(ZoneType.Battlefield).anyMatch(c -> c.getName().equals("Gray Ogre")), "Gray Ogre not cast with the Signet");
    }

    @Test
    public void testSignetKeptWithoutSpell() {
        Game game = signetBoard(false);
        Player ai = game.getPlayers().get(1);
        gameLoopUntilNextPhase(game);
        assertTrue(ai.getCardsIn(ZoneType.Battlefield).anyMatch(c -> c.getName().equals("Rakdos Signet") && !c.isTapped()), "Signet activated for nothing");
    }

    // Imperium (ng5, 27/10): in Main1 the filter was tapped for a creature that then waited for Main2,
    // the pool emptied and the creature was no longer affordable
    @Test
    public void testSignetNotTappedInMain1ForDeferredCreature() {
        Game game = signetBoard(true);
        Player ai = game.getPlayers().get(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        gameLoopUntilNextPhase(game);
        assertTrue(ai.getCardsIn(ZoneType.Battlefield).anyMatch(c -> c.getName().equals("Rakdos Signet") && !c.isTapped()),
                "Signet tapped in Main1 for a creature that waits for Main2");
    }

    @Test
    public void testSignetCast() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard("Swamp", ai);
        addCard("Island", ai);
        addCardToZone("Dimir Signet", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        moveToMain2(game, ai);
        gameLoopUntilNextPhase(game);
        assertTrue(ai.getCardsIn(ZoneType.Battlefield).anyMatch(c -> c.getName().equals("Dimir Signet")), "Dimir Signet not cast");
    }

    // Snowpiquet: Kiora's Follower untaps a tapped land when a spell in hand needs the mana (tester)
    private Game kiorasBoard(boolean withSpell) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard("Kiora's Follower", ai).setSickness(false);
        addCard("Forest", ai);
        Card tapped = addCard("Forest", ai);
        tapped.setTapped(true);
        if (withSpell) {
            addCardToZone("Grizzly Bears", ai, ZoneType.Hand);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        moveToMain2(game, ai);
        return game;
    }

    @Test
    public void testKiorasFollowerUntapsLandForSpell() {
        Game game = kiorasBoard(true);
        Player ai = game.getPlayers().get(1);
        gameLoopUntilNextPhase(game);
        assertTrue(ai.getCardsIn(ZoneType.Battlefield).anyMatch(c -> c.getName().equals("Grizzly Bears")), "Grizzly Bears not cast\n" + gameStateToString(game));
    }

    @Test
    public void testKiorasFollowerKeptWithoutSpell() {
        Game game = kiorasBoard(false);
        Player ai = game.getPlayers().get(1);
        gameLoopUntilNextPhase(game);
        assertTrue(ai.getCardsIn(ZoneType.Battlefield).anyMatch(c -> c.getName().equals("Kiora's Follower") && !c.isTapped()), "Follower tapped for nothing");
    }

    // Dino, Jurassic, Crétacé: Marauding Raptor is cast (body, creature cost reduction)
    @Test
    public void testMaraudingRaptorCast() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard("Mountain", ai);
        addCard("Mountain", ai);
        addCardToZone("Marauding Raptor", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        moveToMain2(game, ai);
        gameLoopUntilNextPhase(game);
        assertTrue(ai.getCardsIn(ZoneType.Battlefield).anyMatch(c -> c.getName().equals("Marauding Raptor")), "Marauding Raptor not cast");
    }

    // Solitaire: one creature at a time, so a mutual sacrifice favours it (tester, 24/10)
    private Game edictBoard(String spell, boolean oppCreature, String ourCreature) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        for (int i = 0; i < 4; i++) {
            addCard("Swamp", ai);
        }
        if (ourCreature != null) {
            addCard(ourCreature, ai);
        }
        if (oppCreature) {
            addCard("Serra Angel", opponent);
        }
        addCardToZone(spell, ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        moveToMain2(game, ai);
        gameLoopUntilNextPhase(game);
        return game;
    }

    private static boolean inGraveyard(Player p, String name) {
        return p.getCardsIn(ZoneType.Graveyard).anyMatch(c -> c.getName().equals(name));
    }

    @Test
    public void testBarterInBloodWithNoCreatureOfOurs() {
        Game game = edictBoard("Barter in Blood", true, null);
        assertTrue(inGraveyard(game.getPlayers().get(0), "Serra Angel"), "Barter in Blood not cast\n" + gameStateToString(game));
    }

    @Test
    public void testInnocentBloodTradesElfForAngel() {
        Game game = edictBoard("Innocent Blood", true, "Llanowar Elves");
        assertTrue(inGraveyard(game.getPlayers().get(0), "Serra Angel"), "Innocent Blood not cast\n" + gameStateToString(game));
    }

    @Test
    public void testInnocentBloodKeptWhenOursIsBetter() {
        Game game = edictBoard("Innocent Blood", true, "Shivan Dragon");
        Player ai = game.getPlayers().get(1);
        assertTrue(ai.getCardsIn(ZoneType.Hand).anyMatch(c -> c.getName().equals("Innocent Blood")), "Innocent Blood cast for a bad trade");
    }

    @Test
    public void testBarterInBloodKeptWithoutTarget() {
        Game game = edictBoard("Barter in Blood", false, "Llanowar Elves");
        Player ai = game.getPlayers().get(1);
        assertTrue(ai.getCardsIn(ZoneType.Hand).anyMatch(c -> c.getName().equals("Barter in Blood")), "Barter in Blood cast with no opposing creature");
    }

    // Patrilock: Isochron Scepter imprints a useful instant, a counterspell first (tester, 24/10)
    @Test
    public void testScepterImprintsCounterspell() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard("Island", ai);
        addCard("Island", ai);
        addCardToZone("Isochron Scepter", ai, ZoneType.Hand);
        addCardToZone("Opt", ai, ZoneType.Hand);
        addCardToZone("Counterspell", ai, ZoneType.Hand);
        addCardToZone("Giant Growth", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        moveToMain2(game, ai);
        gameLoopUntilNextPhase(game);
        Card scepter = findCardWithName(game, "Isochron Scepter");
        assertTrue(scepter.isInZone(ZoneType.Battlefield), "Scepter not cast");
        assertTrue(scepter.getImprintedCards().anyMatch(c -> c.getName().equals("Counterspell")),
                "Counterspell not imprinted\n" + gameStateToString(game));
    }

    private Game scepterWithCounterspell(boolean opponentCasts) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        Card scepter = addCard("Isochron Scepter", ai);
        Card cs = addCardToZone("Counterspell", ai, ZoneType.Exile);
        cs.setExiledWith(scepter);
        cs.setExiledBy(ai);
        scepter.addExiledCard(cs);
        scepter.addImprintedCard(cs);
        addCard("Island", ai);
        addCard("Island", ai);
        for (int i = 0; i < 5; i++) {
            addCard("Plains", opponent);
        }
        addCardToZone("Serra Angel", opponent, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        if (opponentCasts) {
            game.getPhaseHandler().devModeSet(PhaseType.MAIN1, opponent);
            game.getAction().checkStateEffects(true);
            playUntilPhase(game, PhaseType.END_OF_TURN);
        } else {
            moveToMain2(game, ai);
            gameLoopUntilNextPhase(game);
        }
        return game;
    }

    @Test
    public void testScepterCountersInResponse() {
        Game game = scepterWithCounterspell(true);
        assertEquals(countCardsWithName(game, "Serra Angel", ZoneType.Hand), 0, "Serra Angel not cast");
        assertTrue(inGraveyard(game.getPlayers().get(0), "Serra Angel"), "Serra Angel not countered\n" + gameStateToString(game));
    }

    @Test
    public void testScepterNotTappedForNothing() {
        Game game = scepterWithCounterspell(false);
        assertTrue(!findCardWithName(game, "Isochron Scepter").isTapped(), "Scepter tapped with nothing to counter");
    }

    // grenouille (tester): Mistbreath Elder bounces a creature with an ETB at upkeep, never a token
    private Game elderBoard(String other) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard("Mistbreath Elder", ai);
        addCard("Grizzly Bears", ai);
        if (other.equals("Saproling")) {
            addToken("g_1_1_saproling", ai);
        } else {
            addCard(other, ai);
        }
        addCard("Forest", ai);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.CLEANUP, opponent);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.DRAW);
        return game;
    }

    @Test
    public void testMistbreathElderBouncesEtbCreature() {
        Game game = elderBoard("Pond Prophet");
        Player ai = game.getPlayers().get(1);
        assertTrue(ai.getCardsIn(ZoneType.Hand).anyMatch(c -> c.getName().equals("Pond Prophet")), "Pond Prophet not bounced\n" + gameStateToString(game));
        assertEquals(findCardWithName(game, "Mistbreath Elder").getCounters(CounterEnumType.P1P1), 1);
    }

    @Test
    public void testMistbreathElderKeepsToken() {
        Game game = elderBoard("Saproling");
        Player ai = game.getPlayers().get(1);
        assertTrue(ai.getCardsIn(ZoneType.Hand).anyMatch(c -> c.getName().equals("Grizzly Bears")), "Grizzly Bears not bounced\n" + gameStateToString(game));
        assertTrue(ai.getCardsIn(ZoneType.Battlefield).anyMatch(Card::isToken), "token bounced");
    }

    // grenouille (tester): Run Away Together saves a creature or replays an ETB at the opponent's end step
    private Game runAwayBoard(String mine, boolean opponentMurders) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard(mine, ai);
        addCard("Island", ai);
        addCard("Island", ai);
        addCardToZone("Run Away Together", ai, ZoneType.Hand);
        addCard("Grizzly Bears", opponent);
        addCard("Swamp", opponent);
        addCard("Swamp", opponent);
        if (opponentMurders) {
            addCard("Swamp", opponent);
            addCardToZone("Murder", opponent, ZoneType.Hand);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(opponentMurders ? PhaseType.MAIN1 : PhaseType.MAIN2, opponent);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.CLEANUP);
        return game;
    }

    @Test
    public void testRunAwayTogetherSavesFromMurder() {
        Game game = runAwayBoard("Serra Angel", true);
        Player ai = game.getPlayers().get(1);
        assertTrue(inGraveyard(game.getPlayers().get(0), "Murder"), "Murder not cast");
        assertTrue(ai.getCardsIn(ZoneType.Hand).anyMatch(c -> c.getName().equals("Serra Angel")), "Serra Angel not saved\n" + gameStateToString(game));
        assertTrue(game.getPlayers().get(0).getCardsIn(ZoneType.Hand).anyMatch(c -> c.getName().equals("Grizzly Bears")), "Grizzly Bears not bounced");
    }

    @Test
    public void testRunAwayTogetherReplaysEtbAtEndStep() {
        Game game = runAwayBoard("Pond Prophet", false);
        Player ai = game.getPlayers().get(1);
        assertTrue(ai.getCardsIn(ZoneType.Hand).anyMatch(c -> c.getName().equals("Pond Prophet")), "Pond Prophet not bounced\n" + gameStateToString(game));
    }

    @Test
    public void testRunAwayTogetherKeptWithoutReason() {
        Game game = runAwayBoard("Grizzly Bears", false);
        Player ai = game.getPlayers().get(1);
        assertTrue(ai.getCardsIn(ZoneType.Hand).anyMatch(c -> c.getName().equals("Run Away Together")), "Run Away Together cast for nothing");
    }

    // ketramos (tester), Sauron (tester): Lobelia waits for an opposing creature to die, then makes that many Treasures
    private Game lobeliaBoard(boolean withMurder) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        for (int i = 0; i < 6; i++) {
            addCard("Swamp", ai);
        }
        addCardToZone("Lobelia Sackville-Baggins", ai, ZoneType.Hand);
        if (withMurder) {
            addCardToZone("Murder", ai, ZoneType.Hand);
        }
        addCard("Serra Angel", opponent).setSickness(false);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        // with Murder: the opponent's turn (Murder at instant speed); without: our own turn
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, withMurder ? opponent : ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, withMurder ? PhaseType.CLEANUP : PhaseType.END_OF_TURN);
        return game;
    }

    @Test
    public void testLobeliaAfterMurderMakesTreasures() {
        Game game = lobeliaBoard(true);
        Player ai = game.getPlayers().get(1);
        assertTrue(game.getPlayers().get(0).getCardsIn(ZoneType.Exile).anyMatch(c -> c.getName().equals("Serra Angel")), "Serra Angel not exiled by Lobelia\n" + gameStateToString(game));
        assertEquals(countCardsWithName(game, "Treasure Token", ZoneType.Battlefield), 4,
                "no Treasures\n" + gameStateToString(game));
    }

    @Test
    public void testLobeliaHeldWithoutDeadCreature() {
        Game game = lobeliaBoard(false);
        Player ai = game.getPlayers().get(1);
        assertTrue(ai.getCardsIn(ZoneType.Hand).anyMatch(c -> c.getName().equals("Lobelia Sackville-Baggins")),
                "Lobelia cast for nothing on our turn\n" + gameStateToString(game));
    }

    @Test
    public void testLobeliaFlashedAsBodyBeforeOurTurn() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        for (int i = 0; i < 3; i++) {
            addCard("Swamp", ai);
        }
        addCardToZone("Lobelia Sackville-Baggins", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, opponent);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.CLEANUP);
        assertTrue(ai.getCardsIn(ZoneType.Battlefield).anyMatch(c -> c.getName().equals("Lobelia Sackville-Baggins")),
                "Lobelia never cast without a dead creature\n" + gameStateToString(game));
    }

    // Auriok: Dispatch only with metalcraft, exiling the opponent's best creature (during the opponent's turn)
    private Game dispatchBoard(int artifacts) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard("Plains", ai);
        addCard("Plains", ai);
        for (int i = 0; i < artifacts; i++) {
            addCard("Ornithopter", ai);
        }
        addCardToZone("Dispatch", ai, ZoneType.Hand);
        addCard("Serra Angel", opponent).setSickness(false);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, opponent);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.CLEANUP);
        return game;
    }

    @Test
    public void testDispatchExilesWithMetalcraft() {
        Game game = dispatchBoard(3);
        assertTrue(game.getPlayers().get(0).getCardsIn(ZoneType.Exile).anyMatch(c -> c.getName().equals("Serra Angel")),
                "Serra Angel not exiled by Dispatch\n" + gameStateToString(game));
    }

    @Test
    public void testDispatchKeptWithoutMetalcraft() {
        Game game = dispatchBoard(2);
        Player ai = game.getPlayers().get(1);
        assertTrue(ai.getCardsIn(ZoneType.Hand).anyMatch(c -> c.getName().equals("Dispatch")),
                "Dispatch cast without metalcraft\n" + gameStateToString(game));
    }

    // Tymaret: Stronghold Assassin sacrifices its worst creature to destroy a better nonblack one (opponent's turn)
    private Game assassinBoard(String target, boolean withElf) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard("Swamp", ai);
        addCard("Stronghold Assassin", ai).setSickness(false);
        if (withElf) {
            addCard("Llanowar Elves", ai).setSickness(false);
        }
        addCard("Serra Angel", ai).setSickness(false);
        addCard(target, opponent).setSickness(false);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, opponent);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.CLEANUP);
        return game;
    }

    @Test
    public void testStrongholdAssassinTradesElfForAngel() {
        Game game = assassinBoard("Baneslayer Angel", true);
        Player ai = game.getPlayers().get(1);
        assertTrue(game.getPlayers().get(0).getCardsIn(ZoneType.Graveyard).anyMatch(c -> c.getName().equals("Baneslayer Angel")),
                "Baneslayer Angel not destroyed\n" + gameStateToString(game));
        assertTrue(ai.getCardsIn(ZoneType.Battlefield).anyMatch(c -> c.getName().equals("Serra Angel")),
                "Serra Angel sacrificed\n" + gameStateToString(game));
    }

    @Test
    public void testStrongholdAssassinKeepsAngelWithoutFodder() {
        Game game = assassinBoard("Grizzly Bears", false);
        Player ai = game.getPlayers().get(1);
        assertTrue(ai.getCardsIn(ZoneType.Battlefield).anyMatch(c -> c.getName().equals("Serra Angel")),
                "Serra Angel sacrificed for a Grizzly Bears\n" + gameStateToString(game));
    }

    // Moria: Goblin Recruiter stacks a few goblins it can cast soon, not every goblin of the library
    @Test
    public void testGoblinRecruiterStacksThreeGoblins() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCards("Mountain", 3, ai);
        addCardToZone("Goblin Recruiter", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        for (String g : new String[] {"Krenko, Mob Boss", "Goblin Rabblemaster", "Goblin Piledriver",
                "Goblin Warchief", "Goblin Banneret", "Siege-Gang Commander"}) {
            addCardToZone(g, ai, ZoneType.Library);
        }
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertTrue(ai.getCardsIn(ZoneType.Battlefield).anyMatch(c -> c.getName().equals("Goblin Recruiter")),
                "Goblin Recruiter not cast\n" + gameStateToString(game));
        java.util.List<Card> library = new java.util.ArrayList<>(ai.getCardsIn(ZoneType.Library));
        for (int i = 0; i < 3; i++) {
            assertTrue(library.get(i).getType().hasCreatureType("Goblin"), "no goblin at position " + i + "\n" + gameStateToString(game));
        }
        assertTrue(library.get(0).getCMC() <= 4, "top goblin not castable next turn: " + library.get(0));
        long goblinsOnTop = library.subList(0, 6).stream().filter(c -> c.getType().hasCreatureType("Goblin")).count();
        assertTrue(goblinsOnTop < 6, "every goblin stacked on top\n" + gameStateToString(game));
    }

    // Solitaire: Fettergeist (one creature at a time) is cast as a 3/4 flyer and kept through the upkeep
    @Test
    public void testFettergeistCastAndKept() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCards("Island", 3, ai);
        addCardToZone("Fettergeist", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilNextTurn(game);
        playUntilNextTurn(game);
        playUntilPhase(game, PhaseType.MAIN1);
        assertTrue(ai.getCardsIn(ZoneType.Battlefield).anyMatch(c -> c.getName().equals("Fettergeist")),
                "Fettergeist not cast or sacrificed\n" + gameStateToString(game));
    }

    // Cendreline: Incandescent Soulstoke puts a big elemental it cannot cast onto the battlefield to attack with haste
    @Test
    public void testSoulstokeSneaksBigElemental() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCards("Mountain", 2, ai);
        addCard("Incandescent Soulstoke", ai).setSickness(false);
        addCardToZone("Fire Elemental", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertTrue(opponent.getLife() <= 14, "Fire Elemental not put in to attack (life " + opponent.getLife() + ")\n" + gameStateToString(game));
    }

    // Cendreline: a small elemental without ETB is not thrown away by Incandescent Soulstoke
    @Test
    public void testSoulstokeKeepsSmallElemental() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCards("Mountain", 2, ai);
        addCard("Incandescent Soulstoke", ai).setSickness(false);
        addCardToZone("Smokebraider", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilNextTurn(game);
        assertFalse(ai.getCardsIn(ZoneType.Graveyard).anyMatch(c -> c.getName().equals("Smokebraider")),
                "Smokebraider sacrificed for nothing\n" + gameStateToString(game));
    }

    // Snowboard: Alrund (big body + card draw) is cast as a creature and its end step dig resolves
    @Test
    public void testAlrundCastAsBody() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCards("Island", 6, ai);
        addCardToZone("Alrund, God of the Cosmos", ai, ZoneType.Hand);
        for (int i = 0; i < 4; i++) {
            addCardToZone("Colossal Dreadmaw", ai, ZoneType.Hand);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilNextTurn(game);
        assertTrue(ai.getCardsIn(ZoneType.Battlefield).anyMatch(c -> c.getName().equals("Alrund, God of the Cosmos")),
                "Alrund not cast\n" + gameStateToString(game));
    }

    // Soldats: with Odric and three others attacking, the AI picks the opponent's blocks to kill its blockers
    @Test
    public void testOdricBlocksOpponentCreaturesIntoDeath() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard("Odric, Master Tactician", ai).setSickness(false);
        for (int i = 0; i < 3; i++) {
            addCard("Grizzly Bears", ai).setSickness(false);
        }
        addCard("Llanowar Elves", opponent);
        addCard("Llanowar Elves", opponent);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.MAIN2);
        assertEquals(forge.game.card.CardLists.count(opponent.getCardsIn(ZoneType.Graveyard), c -> c.getName().equals("Llanowar Elves")), 2,
                "elves not blocked into our attackers\n" + gameStateToString(game));
        assertEquals(ai.getCreaturesInPlay().size(), 4, "we lost an attacker\n" + gameStateToString(game));
    }

    // Soldats: no blocks at all when our unblocked attack is lethal
    @Test
    public void testOdricLetsEverythingThroughWhenLethal() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard("Odric, Master Tactician", ai).setSickness(false);
        for (int i = 0; i < 3; i++) {
            addCard("Grizzly Bears", ai).setSickness(false);
        }
        addCard("Llanowar Elves", opponent);
        addCard("Llanowar Elves", opponent);
        opponent.setLife(9, null);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.MAIN2);
        assertTrue(game.isGameOver() || opponent.getLife() <= 0, "opponent survived a lethal Odric attack\n" + gameStateToString(game));
    }

    // Crépitou: Cerulean Wisps untaps a tapped Gelectrode (ready to ping again) and draws
    @Test
    public void testCeruleanWispsUntapsGelectrode() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard("Island", ai);
        Card gelectrode = addCard("Gelectrode", ai);
        gelectrode.setSickness(false);
        gelectrode.tap(true, null, null);
        addCardToZone("Cerulean Wisps", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertTrue(ai.getCardsIn(ZoneType.Graveyard).anyMatch(c -> c.getName().equals("Cerulean Wisps")),
                "Cerulean Wisps not cast on the tapped Gelectrode\n" + gameStateToString(game));
        assertFalse(gelectrode.isTapped(), "Gelectrode still tapped\n" + gameStateToString(game));
    }

    // Demogorgon: Covetous Urge takes the opponent's best card (Serra Angel over Llanowar Elves) and casts it
    @Test
    public void testCovetousUrgeStealsAndCastsBestCard() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCards("Swamp", 9, ai);
        addCardToZone("Covetous Urge", ai, ZoneType.Hand);
        addCardToZone("Serra Angel", opponent, ZoneType.Hand);
        addCardToZone("Llanowar Elves", opponent, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertTrue(ai.getCardsIn(ZoneType.Graveyard).anyMatch(c -> c.getName().equals("Covetous Urge")),
                "Covetous Urge not cast\n" + gameStateToString(game));
        assertTrue(ai.getCardsIn(ZoneType.Battlefield).anyMatch(c -> c.getName().equals("Serra Angel")),
                "Serra Angel not stolen and cast\n" + gameStateToString(game));
    }

    // Sacrifice Mutuel: God-Eternal Bontu sacrifices the lands the hand does not need and draws, not the whole board
    @Test
    public void testBontuSacrificesExtraLandsOnly() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCards("Swamp", 9, ai);
        addCard("Serra Angel", ai);
        addCardToZone("God-Eternal Bontu", ai, ZoneType.Hand);
        addCardToZone("Cancel", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertTrue(ai.getCardsIn(ZoneType.Battlefield).anyMatch(c -> c.getName().equals("God-Eternal Bontu")),
                "Bontu not cast\n" + gameStateToString(game));
        assertTrue(ai.getCardsIn(ZoneType.Battlefield).anyMatch(c -> c.getName().equals("Serra Angel")),
                "Serra Angel sacrificed\n" + gameStateToString(game));
        long lands = ai.getCardsIn(ZoneType.Battlefield).stream().filter(Card::isLand).count();
        assertEquals(lands, 4L, "lands kept\n" + gameStateToString(game));
        long bears = ai.getCardsIn(ZoneType.Hand).stream().filter(c -> c.getName().equals("Runeclaw Bear")).count()
                + ai.getCardsIn(ZoneType.Battlefield).stream().filter(c -> c.getName().equals("Runeclaw Bear")).count();
        assertEquals(bears, 5L, "cards drawn\n" + gameStateToString(game));
    }

    // Oasis: Magus of the Bazaar at the end of the opponent's turn discards the embalm cards into Drake Haven
    @Test
    public void testMagusOfTheBazaarDiscardsEmbalmCards() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCards("Island", 3, ai);
        addCard("Drake Haven", ai);
        addCard("Magus of the Bazaar", ai).setSickness(false);
        addCardToZone("Sacred Cat", ai, ZoneType.Hand);
        addCardToZone("Honored Hydra", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, opponent);
        game.getAction().checkStateEffects(true);
        playUntilNextTurn(game);
        assertTrue(ai.getCardsIn(ZoneType.Graveyard).anyMatch(c -> c.getName().equals("Sacred Cat")),
                "Sacred Cat not discarded\n" + gameStateToString(game));
        assertTrue(ai.getCardsIn(ZoneType.Graveyard).anyMatch(c -> c.getName().equals("Honored Hydra")),
                "Honored Hydra not discarded\n" + gameStateToString(game));
    }

    // Oasis: Magus of the Bazaar is not used to throw away a hand with nothing to gain from the graveyard
    @Test
    public void testMagusOfTheBazaarKeepsPlainHand() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCards("Island", 3, ai);
        addCard("Magus of the Bazaar", ai).setSickness(false);
        addCardToZone("Serra Angel", ai, ZoneType.Hand);
        addCardToZone("Lightning Bolt", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, opponent);
        game.getAction().checkStateEffects(true);
        playUntilNextTurn(game);
        assertTrue(ai.getCardsIn(ZoneType.Graveyard).isEmpty(), "Magus of the Bazaar used for nothing\n" + gameStateToString(game));
    }

    // generic discard rule: a card that still works from the graveyard (Hellspark Elemental, unearth) goes before
    // a live card, here for the hand size at cleanup (before: the worst creature, a Raging Goblin)
    @Test
    public void testDiscardPrefersUnearthCard() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        for (int i = 0; i < 3; i++) {
            addCard("Mountain", ai).setTapped(true);
        }
        for (int i = 0; i < 7; i++) {
            addCardToZone("Raging Goblin", ai, ZoneType.Hand);
        }
        addCardToZone("Hellspark Elemental", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        playUntilNextTurn(game);
        assertTrue(ai.getCardsIn(ZoneType.Graveyard).anyMatch(c -> c.getName().equals("Hellspark Elemental")),
                "Hellspark Elemental not discarded first\n" + gameStateToString(game));
    }

    // Nothing Gulch: Akul the Unrepentant trades three tokens for a Terror of the Peaks we cannot cast
    @Test
    public void testAkulTradesTokensForTerror() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCards("Swamp", 2, ai);
        addCards("Mountain", 2, ai);
        addCard("Akul the Unrepentant", ai).setSickness(false);
        for (int i = 0; i < 3; i++) {
            addToken("g_1_1_saproling", ai);
        }
        addCardToZone("Terror of the Peaks", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertTrue(ai.getCardsIn(ZoneType.Battlefield).anyMatch(c -> c.getName().equals("Terror of the Peaks")),
                "Terror of the Peaks not put in with Akul\n" + gameStateToString(game));
    }

    // Nothing Gulch: Akul does not sacrifice three angels for a creature it could cast anyway
    @Test
    public void testAkulKeepsGoodCreatures() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCards("Swamp", 2, ai);
        addCards("Mountain", 2, ai);
        addCard("Akul the Unrepentant", ai).setSickness(false);
        for (int i = 0; i < 3; i++) {
            addCard("Serra Angel", ai).setSickness(false);
        }
        addCardToZone("Terror of the Peaks", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        playUntilNextTurn(game);
        assertEquals(countCardsWithName(game, "Serra Angel", ZoneType.Battlefield), 3,
                "Serra Angels sacrificed to Akul\n" + gameStateToString(game));
    }

    // general sacrifice rule (FodderAi): Goblin Bombardment, no AIPreference, trades a goblin token for an elf
    @Test
    public void testFodderBombardmentTradesTokenForElf() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        // the profile the simulations run with (SACRIFICE_DEFAULT_PREF_ENABLE=false)
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        addCard("Mountain", ai);
        addCard("Goblin Bombardment", ai);
        addToken("r_1_1_goblin", ai);
        addCard("Serra Angel", ai).setSickness(true);
        addCard("Llanowar Elves", opponent);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertTrue(opponent.getCardsIn(ZoneType.Graveyard).anyMatch(c -> c.getName().equals("Llanowar Elves")),
                "Llanowar Elves not shot\n" + gameStateToString(game));
        assertTrue(ai.getCardsIn(ZoneType.Battlefield).anyMatch(c -> c.getName().equals("Serra Angel")),
                "Serra Angel sacrificed\n" + gameStateToString(game));
    }

    // general sacrifice rule: a free scry outlet does not eat a token
    @Test
    public void testFodderVisceraSeerKeepsTokens() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        // the profile the simulations run with (SACRIFICE_DEFAULT_PREF_ENABLE=false)
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        addCard("Swamp", ai);
        addCard("Viscera Seer", ai).setSickness(false);
        addTokens("r_1_1_goblin", 2, ai);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        playUntilNextTurn(game);
        assertEquals(2, forge.game.card.CardLists.filter(ai.getCardsIn(ZoneType.Battlefield), Card::isToken).size(),
                "tokens sacrificed to scry\n" + gameStateToString(game));
    }

    // general rule 10 (keep the return for the loop): with Melira still in hand, Safehold Elite's persist is not spent on scry 1
    @Test
    public void testPersistKeptForMeliraLoop() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        addCard("Swamp", ai);
        addCard("Viscera Seer", ai).setSickness(false);
        Card elite = addCard("Safehold Elite", ai);
        elite.setSickness(false);
        addCardToZone("Melira, Sylvok Outcast", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.UPKEEP, opponent);
        game.getAction().checkStateEffects(true);
        playUntilNextTurn(game);
        assertTrue(ai.getCardsIn(ZoneType.Battlefield).anyMatch(c -> c.getName().equals("Safehold Elite")
                && c.getCounters(forge.game.card.CounterEnumType.M1M1) == 0),
                "Safehold Elite's persist spent before Melira\n" + gameStateToString(game));
    }

    // general rule 10: without a loop piece left, persist is still free fodder for the Seer
    @Test
    public void testPersistFreeWithoutLoopPiece() {
        // magic-tools: fixed seed, the result depended on the random state left by the rest of the suite
        forge.util.MyRandom.setRandom(new java.util.Random(1));
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        addCard("Swamp", ai);
        addCard("Viscera Seer", ai).setSickness(false);
        addCard("Safehold Elite", ai).setSickness(false);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.UPKEEP, opponent);
        game.getAction().checkStateEffects(true);
        playUntilNextTurn(game);
        assertTrue(ai.getCardsIn(ZoneType.Battlefield).anyMatch(c -> c.getName().equals("Safehold Elite")
                && c.getCounters(forge.game.card.CounterEnumType.M1M1) == 1),
                "Safehold Elite not sacrificed\n" + gameStateToString(game));
    }

    // general rule 5 (blink): Ephemerate replays Mulldrifter's ETB, not Phyrexian Dreadnought (it would sacrifice itself)
    @Test
    public void testBlinkPrefersEtbWorthMost() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        addCards("Plains", 3, ai);
        Card drifter = addCard("Mulldrifter", ai);
        Card dread = addCard("Phyrexian Dreadnought", ai);
        drifter.setSickness(false);
        dread.setSickness(false);
        addCardToZone("Ephemerate", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.END_OF_TURN, opponent);
        game.getAction().checkStateEffects(true);
        playUntilNextTurn(game);
        assertTrue(ai.getCardsIn(ZoneType.Battlefield).anyMatch(c -> c.getName().equals("Phyrexian Dreadnought")),
                "Phyrexian Dreadnought blinked and sacrificed\n" + gameStateToString(game));
        assertFalse(ai.getCardsIn(ZoneType.Hand).anyMatch(c -> c.getName().equals("Ephemerate")),
                "Ephemerate not cast\n" + gameStateToString(game));
        assertTrue(ai.getCardsIn(ZoneType.Hand).stream().filter(c -> c.getName().equals("Runeclaw Bear")).count() >= 2,
                "Mulldrifter not blinked\n" + gameStateToString(game));
    }

    // general rule 5 (blink): no creature gains anything, Ephemerate stays in hand
    @Test
    public void testBlinkKeptWhenNoEtbWorthIt() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        addCards("Plains", 3, ai);
        Card dread = addCard("Phyrexian Dreadnought", ai);
        dread.setSickness(false);
        addCardToZone("Ephemerate", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.END_OF_TURN, opponent);
        game.getAction().checkStateEffects(true);
        playUntilNextTurn(game);
        assertTrue(ai.getCardsIn(ZoneType.Battlefield).anyMatch(c -> c.getName().equals("Phyrexian Dreadnought")),
                "Phyrexian Dreadnought blinked and sacrificed\n" + gameStateToString(game));
    }

    // general rule 5 (blink): a modal ETB (Charming Prince) must not crash the blink valuation; the crash cancelled
    // the whole priority and Ephemerate stayed in hand
    @Test
    public void testBlinkWithModalEtb() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        addCards("Plains", 3, ai);
        Card prince = addCard("Charming Prince", ai);
        Card drifter = addCard("Mulldrifter", ai);
        prince.setSickness(false);
        drifter.setSickness(false);
        // as on a permanent whose ETB never fired on this object (blinked, returned, put onto the battlefield)
        for (forge.game.trigger.Trigger tr : prince.getTriggers()) {
            tr.ensureAbility().setActivatingPlayer(null);
        }
        addCardToZone("Ephemerate", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.END_OF_TURN, opponent);
        game.getAction().checkStateEffects(true);
        playUntilNextTurn(game);
        assertFalse(ai.getCardsIn(ZoneType.Hand).anyMatch(c -> c.getName().equals("Ephemerate")),
                "Ephemerate not cast\n" + gameStateToString(game));
    }
}
