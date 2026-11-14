package forge.ai;

import java.util.ArrayList;
import java.util.List;

import forge.game.Game;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardCollectionView;
import forge.game.card.CardLists;
import forge.game.phase.PhaseHandler;
import forge.game.player.Player;
import forge.game.spellability.AbilitySub;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rules 277 and 278 (D159, tester 10/11 12:53, plateau, deck cus_jrrt, a test game):
 * the Ents deck stayed on 2-3 lands for seven turns with three Jeune Enture in play, and Ent de Fangorn never put a
 * land from the hand onto the battlefield.
 * <ul>
 * <li>Rule 277: a targeted "return a land you own from the battlefield to your hand" whose chain makes mana (Jeune
 * Enture: return target land, add {G} if a Forest) was only ever used to save a permanent. It is used on our own
 * basic land when that land can come back this turn: the land drop is still free and no other land waits in the hand,
 * or an untapped ability of ours puts lands from the hand onto the battlefield. A tapped land goes first (its mana is
 * already spent), a Forest next.</li>
 * <li>Rule 278: an ability that puts lands from the hand onto the battlefield (Ent de Fangorn, Skyshroud Ranger) is
 * offered before the land drop, as PlayBeforeLandDrop cards are: once the land is played the hand may have none left
 * and the ability is dead for the turn.</li>
 * <li>Rule 279 (D161, tester 10/11 14:13, Gardien de la Forêt, a test game): a "choose one" between
 * "put a Forest from your hand onto the battlefield" and "return a Forest you own from the battlefield or your
 * graveyard to your hand" took the return mode at random and then returned nothing, with a Forest in hand. The put
 * mode is taken whenever the hand holds a land it can put; otherwise the return mode when it brings back a land from
 * the graveyard (a free card) or a land of ours that the free land drop replays. The returned land is then the one
 * from the graveyard, or a tapped one.</li>
 * </ul>
 */
public final class LandReplayAi {
    private LandReplayAi() { }

    private static boolean ourQuietMain(final Player ai) {
        final Game game = ai.getGame();
        final PhaseHandler ph = game.getPhaseHandler();
        return ph.isPlayerTurn(ai) && ph.getPhase().isMain() && game.getStack().isEmpty();
    }

    /** an activated ChangeZone Hand to Battlefield of lands (Ent de Fangorn, Skyshroud Ranger) */
    public static boolean putsLandsFromHand(final SpellAbility sa) {
        return sa != null && sa.isActivatedAbility() && sa.getApi() == ApiType.ChangeZone
                && "Hand".equals(sa.getParam("Origin")) && "Battlefield".equals(sa.getParam("Destination"))
                && sa.hasParam("ChangeType") && sa.getParam("ChangeType").contains("Land");
    }

    private static List<SpellAbility> putters(final Player ai, final Card except) {
        final List<SpellAbility> out = new ArrayList<>();
        for (final Card c : ai.getCardsIn(ZoneType.Battlefield)) {
            if (c == except || c.isLand()) {
                continue;
            }
            for (final SpellAbility sa : c.getSpellAbilities()) {
                if (!putsLandsFromHand(sa)) {
                    continue;
                }
                sa.setActivatingPlayer(ai);
                if (sa.canPlay() && ComputerUtilCost.canPayCost(sa, ai, false)) {
                    out.add(sa);
                }
            }
        }
        return out;
    }

    /** rule 278: the abilities to offer before the land drop */
    public static List<SpellAbility> beforeLand(final Player ai) {
        if (!NewAi.on(ai, 278) || !ourQuietMain(ai)) {
            return new ArrayList<>();
        }
        boolean landInHand = false;
        for (final Card c : ai.getCardsIn(ZoneType.Hand)) {
            if (c.isLand()) {
                landInHand = true;
                break;
            }
        }
        return landInHand ? putters(ai, null) : new ArrayList<>();
    }

    /** rule 278: the putter itself is played on our main phase while the hand holds a land */
    public static boolean putNow(final Player ai, final SpellAbility sa) {
        if (!NewAi.on(ai, 278) || !putsLandsFromHand(sa) || !ourQuietMain(ai)) {
            return false;
        }
        for (final Card c : ai.getCardsIn(ZoneType.Hand)) {
            if (c.isLand()) {
                return true;
            }
        }
        return false;
    }

    /** rule 277: true (target set) when the bounce of our own land is a replay that makes mana */
    public static boolean bounceToReplay(final Player ai, final SpellAbility sa) {
        if (!NewAi.on(ai, 277) || sa.getApi() != ApiType.ChangeZone || !sa.usesTargeting()
                || !"Battlefield".equals(sa.getParam("Origin")) || !"Hand".equals(sa.getParam("Destination"))
                || sa.findSubAbilityByType(ApiType.Mana) == null || !ourQuietMain(ai)) {
            return false;
        }
        final Card source = sa.getHostCard();
        boolean landInHand = false;
        for (final Card c : ai.getCardsIn(ZoneType.Hand)) {
            if (c.isLand()) {
                landInHand = true;
                break;
            }
        }
        final boolean dropFree = !landInHand && ai.getLandsPlayedThisTurn() < ai.getMaxLandPlays();
        if (!dropFree && putters(ai, source).isEmpty()) {
            return false;
        }
        Card best = null;
        int bestScore = -1;
        for (final Card c : ai.getCardsIn(ZoneType.Battlefield)) {
            if (!c.isLand() || !c.isBasicLand() || c == source || !c.getOwner().equals(ai) || !sa.canTarget(c)) {
                continue;
            }
            final int score = (c.isTapped() ? 2 : 0) + (c.getType().hasSubtype("Forest") ? 1 : 0);
            if (score > bestScore) {
                best = c;
                bestScore = score;
            }
        }
        if (best == null || bestScore < 1) {
            return false;
        }
        sa.resetTargets();
        sa.getTargets().add(best);
        return true;
    }

    private static boolean allLands(final CardCollectionView cards) {
        if (cards.isEmpty()) {
            return false;
        }
        for (final Card c : cards) {
            if (!c.isLand()) {
                return false;
            }
        }
        return true;
    }

    private static CardCollection validIn(final Player ai, final SpellAbility sub, final ZoneType zone) {
        return CardLists.getValidCards(ai.getCardsIn(zone), sub.getParam("ChangeType"), ai, sub.getHostCard(), sub);
    }

    private static boolean hiddenLandMove(final SpellAbility sub, final String destination) {
        return sub.getApi() == ApiType.ChangeZone && !sub.usesTargeting() && sub.hasParam("ChangeType")
                && destination.equals(sub.getParam("Destination")) && sub.hasParam("Origin");
    }

    /** rule 279: the land mode of a "choose one", or null */
    public static AbilitySub charmLandMode(final Player ai, final List<AbilitySub> choices, final int num) {
        if (num != 1 || !NewAi.on(ai, 279)) {
            return null;
        }
        for (final AbilitySub sub : choices) {
            sub.setActivatingPlayer(ai);
            if (hiddenLandMove(sub, "Battlefield") && sub.getParam("Origin").contains("Hand")
                    && allLands(validIn(ai, sub, ZoneType.Hand))) {
                return sub;
            }
        }
        boolean landInHand = false;
        for (final Card c : ai.getCardsIn(ZoneType.Hand)) {
            if (c.isLand()) {
                landInHand = true;
                break;
            }
        }
        final boolean dropFree = !landInHand && ai.getLandsPlayedThisTurn() < ai.getMaxLandPlays() && ourMain(ai);
        for (final AbilitySub sub : choices) {
            if (!hiddenLandMove(sub, "Hand")) {
                continue;
            }
            final String origin = sub.getParam("Origin");
            if (origin.contains("Graveyard") && allLands(validIn(ai, sub, ZoneType.Graveyard))) {
                return sub;
            }
            if (dropFree && origin.contains("Battlefield") && allLands(validIn(ai, sub, ZoneType.Battlefield))) {
                return sub;
            }
        }
        return null;
    }

    private static boolean ourMain(final Player ai) {
        final PhaseHandler ph = ai.getGame().getPhaseHandler();
        return ph.isPlayerTurn(ai) && ph.getPhase().isMain();
    }

    /** rule 279: which of our lands comes back to the hand (graveyard first, then a tapped one), or null */
    public static Card chooseReturnedLand(final Player decider, final SpellAbility sa, final List<Card> fetchList) {
        if (!NewAi.on(decider, 279) || sa.usesTargeting() || fetchList.isEmpty()) {
            return null;
        }
        for (final Card c : fetchList) {
            if (!c.isLand() || !decider.equals(c.getOwner())) {
                return null;
            }
        }
        boolean landInHand = false;
        for (final Card c : decider.getCardsIn(ZoneType.Hand)) {
            if (c.isLand()) {
                landInHand = true;
                break;
            }
        }
        final boolean dropFree = !landInHand && decider.getLandsPlayedThisTurn() < decider.getMaxLandPlays() && ourMain(decider);
        Card best = null;
        int bestScore = -1;
        for (final Card c : fetchList) {
            if (c == sa.getHostCard() || (!c.isInZone(ZoneType.Graveyard) && !dropFree)) {
                continue;
            }
            final int score = (c.isInZone(ZoneType.Graveyard) ? 4 : 0) + (c.isTapped() ? 2 : 0) + (c.isBasicLand() ? 1 : 0);
            if (score > bestScore) {
                best = c;
                bestScore = score;
            }
        }
        return best;
    }
}
