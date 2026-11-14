package forge.ai;

import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardLists;
import forge.game.cost.Cost;
import forge.game.cost.CostExile;
import forge.game.cost.CostPart;
import forge.game.cost.CostPayLife;
import forge.game.cost.CostReturn;
import forge.game.player.Player;
import forge.game.staticability.StaticAbility;
import forge.game.staticability.StaticAbilityMode;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 173 (forge-v211 replays, merfolk against Kavaron). With six Islands, two Eclipsed Merrow and
 * Force of Will in hand, the AI cast nothing for eight turns: rule 22 kept five mana open for Force of Will, a
 * counterspell it can cast for free by exiling one of the blue cards it was holding back.
 * General rule: a counterspell whose alternative cost uses no mana (exile a card from hand, pay life, return a
 * permanent: Force of Will, Force of Negation, Daze) needs no mana kept open, as long as that cost can still be paid
 * once the spell we are about to cast has left the hand.
 */
public final class FreeCounterAi {
    private FreeCounterAi() { }

    /** The counterspell {@code counter} can be cast without mana after {@code host} is cast. */
    public static boolean castsForFree(final Player ai, final Card counter, final Card host) {
        for (final StaticAbility st : counter.getStaticAbilities()) {
            if (!st.checkMode(StaticAbilityMode.AlternativeCost) || !st.hasParam("Cost")) {
                continue;
            }
            final String valid = st.getParam("ValidSA");
            if (valid == null || !valid.contains("Self")) {
                continue;
            }
            final Cost cost = new Cost(st.getParam("Cost"), false);
            if (cost.getTotalMana() != null && cost.getTotalMana().getCMC() > 0) {
                continue;
            }
            if (payableWithout(ai, cost, counter, host)) {
                return true;
            }
        }
        return false;
    }

    static boolean payableWithout(final Player ai, final Cost cost, final Card counter, final Card host) {
        boolean any = false;
        for (final CostPart part : cost.getCostParts()) {
            final Integer amount = part.convertAmount();
            if (amount == null) {
                return false;
            }
            if (part instanceof CostPayLife) {
                if (!ai.canLoseLife() || ai.getLife() <= amount) {
                    return false;
                }
            } else if (part instanceof CostExile && ((CostExile) part).getFrom().contains(ZoneType.Hand)) {
                final CardCollection hand = new CardCollection(ai.getCardsIn(ZoneType.Hand));
                hand.remove(counter);
                hand.remove(host);
                if (CardLists.getValidCards(hand, part.getType().split(";"), ai, counter, null).size() < amount) {
                    return false;
                }
            } else if (part instanceof CostReturn) {
                final CardCollection mine = new CardCollection(ai.getCardsIn(ZoneType.Battlefield));
                if (CardLists.getValidCards(mine, part.getType().split(";"), ai, counter, null).size() < amount) {
                    return false;
                }
            } else {
                return false;
            }
            any = true;
        }
        return any;
    }
}
