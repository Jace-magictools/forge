package forge.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import forge.game.card.Card;
import forge.game.cost.Cost;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 67 (a tester reports 29/10, ElfBall): stock AI casts the most expensive spell first, so
 * Elvish Harbinger (3) went before Llanowar Elves, Fyndhorn Elves and Birchlore Rangers, and Birchlore Rangers
 * (needs two Elves) before Llanowar Elves. Early on, a permanent that taps for mana on its own goes first,
 * as long as the mana left after it is still spent by the other cards in hand (a lone 3-drop threat stays first)
 * and no cheaper one waits in hand.
 */
public final class ManaBaseFirstAi {
    private ManaBaseFirstAi() { }

    /** at most this many mana sources in play: past that, the mana base is built */
    private static final int MAX_SOURCES = 5;

    public static boolean simpleManaSource(final Card c) {
        if (c == null || c.isLand() || !c.isPermanent()) {
            return false;
        }
        for (final SpellAbility ma : c.getManaAbilities()) {
            final Cost cost = ma.getPayCosts();
            if (cost != null && cost.hasTapCost() && cost.getCostParts().size() == 1 && !ma.hasParam("ActivationLimit")) {
                return true;
            }
        }
        return false;
    }

    public static boolean goesFirst(final Player ai, final SpellAbility sa) {
        final Card source = sa.getHostCard();
        if (!sa.isSpell() || source == null || !source.isInZone(ZoneType.Hand) || !simpleManaSource(source)) {
            return false;
        }
        int sources = 0;
        for (final Card c : ai.getCardsIn(ZoneType.Battlefield)) {
            if (c.isLand() || simpleManaSource(c)) {
                sources++;
            }
        }
        if (sources > MAX_SOURCES) {
            return false;
        }
        final int cmc = sa.getPayCosts().getTotalMana().getCMC();
        final int left = ComputerUtilMana.getAvailableManaEstimate(ai) - cmc;
        if (left < 0) {
            return false;
        }
        // the mana left must still be spent this turn by the other spells in hand (cheapest first)
        final List<Integer> others = new ArrayList<>();
        for (final Card c : ai.getCardsIn(ZoneType.Hand)) {
            if (c == source || c.isLand() || c.getCMC() <= 0) {
                continue;
            }
            // a cheaper dork comes first (Elvish Harbinger taps for mana too, after the elves)
            if (c.getCMC() < cmc && simpleManaSource(c)) {
                return false;
            }
            others.add(c.getCMC());
        }
        Collections.sort(others);
        int spent = 0;
        for (final int o : others) {
            if (spent + o <= left) {
                spent += o;
            }
        }
        // alone in hand, nothing to put it before: stock timing (main 2)
        return !others.isEmpty() && spent == left;
    }
}
