package forge.ai;

import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 185. A copy that lands under our control keeps the legendary supertype (unless NonLegendary):
 * if we already control a legendary permanent with that name, the legend rule kills one of the two at once and the
 * whole cost is wasted ([[Dino DNA]] copying the opponent's [[Isperia, Supreme Judge]] every turn, 6 mana each time,
 * while [[Agonasaur Rex]] stayed in hand). Read from the ability, no card named.
 */
public final class LegendCopyAi {
    private LegendCopyAi() {
    }

    public static boolean diesToLegendRule(final Player ai, final SpellAbility sa, final Card c) {
        if (sa.hasParam("NonLegendary") || !c.getType().isLegendary()) {
            return false;
        }
        final String ctrl = sa.getParam("Controller");
        if (ctrl != null && !"You".equals(ctrl)) {
            return false;
        }
        for (Card p : ai.getCardsIn(ZoneType.Battlefield)) {
            if (p != c && p.getType().isLegendary() && p.sharesNameWith(c)) {
                return true;
            }
        }
        return false;
    }
}
