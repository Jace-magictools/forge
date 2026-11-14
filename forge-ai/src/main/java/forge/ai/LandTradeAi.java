package forge.ai;

import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardCollectionView;
import forge.game.card.CardLists;
import forge.game.card.CardPredicates;
import forge.game.phase.PhaseHandler;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 11, land for land. Raze ("sacrifice a land: destroy target land") was never cast: the
 * sacrifice preference only gives up a land once six are in play (ComputerUtil.getCardPreference), and the
 * card was marked AI:RemoveDeck:All. A land destruction deck (Casse Terrain) trades lands on purpose.
 * General rule: sacrificing one of our lands to destroy an opponent's land is an even trade, accepted when we
 * keep at least as many lands as the opponent and either have another land in hand, are already ahead on
 * lands, or the opponent is still on three lands or fewer (mana denial hurts most early).
 * Also: Forge held sorcery-speed land destruction at random (ComputerUtilCard.useRemovalNow, chance ~0.7 per
 * turn); a land is never a better target later, so it is cast in our second main phase.
 * Rule 23: Strip Mine and Wasteland were almost never activated in a land destruction deck (Pox: 1 activation,
 * ~80 refusals): DestroyAi.doLandForLandRemovalLogic wants a high-priority target or a lock and refuses with
 * three lands or fewer. When our deck counts at least LD_DECK_MIN nonland cards that destroy lands, the land
 * itself is the same even trade as Raze (same conditions as landToTrade).
 */
public final class LandTradeAi {
    private LandTradeAi() { }

    public static boolean destroysLand(final SpellAbility sa) {
        if (sa == null || sa.getApi() != ApiType.Destroy || !sa.usesTargeting()) {
            return false;
        }
        for (final String valid : sa.getTargetRestrictions().getValidTgts()) {
            if (valid.contains("Land")) {
                return true;
            }
        }
        return false;
    }

    /** Our own sorcery-speed spell destroying an opponent's land in our second main phase. */
    public static boolean castNow(final SpellAbility sa, final Card target, final Player ai) {
        final PhaseHandler ph = ai.getGame().getPhaseHandler();
        return sa.isSpell() && destroysLand(sa) && target.isLand() && target.getController().isOpponentOf(ai)
                && ph.isPlayerTurn(ai) && ph.is(PhaseType.MAIN2) && SpellAbilityAi.isSorcerySpeed(sa, ai);
    }

    public static final int LD_DECK_MIN = 4;

    /** Our deck (all zones) has at least LD_DECK_MIN nonland cards destroying a land. */
    public static boolean isLandDestructionDeck(final Player ai) {
        int count = 0;
        for (final Card c : ai.getCardsIn(java.util.EnumSet.of(ZoneType.Library, ZoneType.Hand, ZoneType.Battlefield, ZoneType.Graveyard, ZoneType.Exile))) {
            if (c.isLand() || c.isToken()) {
                continue;
            }
            for (final SpellAbility sa : c.getAllSpellAbilities()) {
                if (destroysLand(sa) || destroysLand(sa.getSubAbility())) {
                    count++;
                    break;
                }
            }
            if (count >= LD_DECK_MIN) {
                return true;
            }
        }
        return false;
    }

    /** Lands-for-land trade acceptable: we keep at least as many lands as the strongest opponent. */
    public static boolean evenTrade(final Player ai) {
        final int ours = CardLists.count(ai.getCardsIn(ZoneType.Battlefield), CardPredicates.LANDS_PRODUCING_MANA);
        int theirs = 0;
        for (final Player opp : ai.getOpponents()) {
            theirs = Math.max(theirs, opp.getLandsInPlay().size());
        }
        if (theirs == 0 || ours < 2 || ours < theirs) {
            return false;
        }
        final int inHand = CardLists.count(ai.getCardsIn(ZoneType.Hand), CardPredicates.LANDS);
        return !(inHand == 0 && ours == theirs && theirs > 3);
    }

    /** Rule 23: Strip Mine / Wasteland in a land destruction deck. */
    public static boolean stripInLandDestructionDeck(final Player ai, final Card target) {
        return target != null && target.getController().isOpponentOf(ai) && evenTrade(ai) && isLandDestructionDeck(ai);
    }

    public static Card landToTrade(final Player ai, final CardCollectionView typeList, final SpellAbility sa) {
        if (!destroysLand(sa)) {
            return null;
        }
        final CardCollection lands = CardLists.filter(typeList, CardPredicates.LANDS);
        if (lands.isEmpty()) {
            return null;
        }
        if (!evenTrade(ai)) {
            return null;
        }
        return ComputerUtilCard.getWorstLand(lands);
    }
}
