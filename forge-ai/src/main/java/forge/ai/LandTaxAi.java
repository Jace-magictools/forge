package forge.ai;

import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardLists;
import forge.game.player.Player;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 106 (a tester report 30/10 10:29, Ork - Profanation turn 5): Ork had Land Tax in play and Profanation
 * still played its fourth land with nothing new to cast, feeding Ork three basics each upkeep. General rule: an
 * opponent's permanent that triggers at its upkeep "if an opponent controls more lands than you" (Land Tax, Keeper of
 * the Accord, Priest of the Blessed Graf, Discerning Financier) makes our land drop wait when we already have at least
 * as many lands as that player, unless the land makes a spell of our hand castable this turn or the hand is full.
 */
public final class LandTaxAi {
    private LandTaxAi() { }

    /** a trigger of the card at its controller's upkeep that checks an opponent's land count above theirs */
    static boolean punishesMoreLands(final Card card) {
        for (final Trigger t : card.getTriggers()) {
            if (t.getMode() != TriggerType.Phase || !t.hasParam("CheckSVar")) {
                continue;
            }
            final String compare = t.getParamOrDefault("SVarCompare", "");
            if (!compare.startsWith("GT") && !compare.startsWith("GE")) {
                continue;
            }
            final String svar = card.getSVar(t.getParam("CheckSVar"));
            if (svar != null && svar.contains("PlayerCountOpponents") && svar.contains("Land")) {
                return true;
            }
        }
        return false;
    }

    /** an opponent whose land-count trigger our next land would switch on (or keep on) */
    static boolean landFeedsOpponent(final Player ai) {
        final int ours = ai.getLandsInPlay().size();
        for (final Player opp : ai.getOpponents()) {
            if (ours < opp.getLandsInPlay().size()) {
                continue;
            }
            for (final Card c : opp.getCardsIn(ZoneType.Battlefield)) {
                if (punishesMoreLands(c)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** the land drop waits: it would feed the opponent's Land Tax and brings nothing this turn */
    public static boolean holdLandDrop(final Player ai, final CardCollection landsWannaPlay) {
        if (landsWannaPlay == null || landsWannaPlay.isEmpty() || !landFeedsOpponent(ai)) {
            return false;
        }
        if (ai.getCardsIn(ZoneType.Hand).size() > ai.getMaxHandSize()) {
            return false;
        }
        int inc = 0;
        for (final Card land : landsWannaPlay) {
            inc = Math.max(inc, land.getMaxManaProduced());
        }
        if (inc == 0) {
            return false;
        }
        final int mana = TapLandColorAi.landPlayManaEstimate(ai);
        final CardCollection spells = CardLists.filter(ai.getCardsIn(ZoneType.Hand), c -> !c.isLand());
        for (final Card c : spells) {
            final int cmc = c.getCMC();
            if (cmc > mana && cmc <= mana + inc) {
                return false;
            }
        }
        return true;
    }
}
