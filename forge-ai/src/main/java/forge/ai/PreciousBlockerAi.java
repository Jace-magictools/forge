package forge.ai;

import java.util.List;

import forge.game.card.Card;
import forge.game.combat.Combat;
import forge.game.combat.CombatUtil;
import forge.game.player.Player;
import forge.game.staticability.StaticAbility;
import forge.game.staticability.StaticAbilityMode;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;

/**
 * magic-tools: rule 78, a gang block exposes the least precious blocker (tester, 29/10 13:13, Avacyn: Ajani's Pridemate
 * and Righteous Valkyrie double-blocked Voracious Hatchling, which killed the Valkyrie, while Serra Avenger could take
 * its place; "the Valkyrie is very precious"). Stock AI always led the gang with its best creature, so the attacker
 * killed it. The worth of a blocker adds to its creature score what it brings to the rest of the team: a static
 * that pumps our creatures (half if conditional), 40 for a trigger on our other creatures entering or on our life
 * gain. The same worth keeps such an engine out of the random trades.
 */
public final class PreciousBlockerAi {
    private PreciousBlockerAi() { }

    /** Creature score plus what the card brings to our other creatures. */
    public static int worth(final Player ai, final Card c) {
        return ComputerUtilCard.evaluateCreature(c) + engineBonus(ai, c);
    }

    public static int engineBonus(final Player ai, final Card c) {
        int bonus = 0;
        for (final StaticAbility st : c.getStaticAbilities()) {
            if (!st.getMode().contains(StaticAbilityMode.Continuous) || !st.hasParam("Affected")) {
                continue;
            }
            final String affected = st.getParam("Affected");
            if (!affected.startsWith("Creature") || !affected.contains("YouCtrl")) {
                continue;
            }
            final int pow = amount(st.getParam("AddPower"));
            final int tou = amount(st.getParam("AddToughness"));
            if (pow <= 0 && tou <= 0) {
                continue;
            }
            final boolean conditional = st.hasParam("CheckSVar") || st.hasParam("IsPresent") || st.hasParam("Condition");
            final String[] restrictions = affected.split(",");
            int each = 0;
            for (final Card other : ai.getCreaturesInPlay()) {
                if (!other.equals(c) && other.isValid(restrictions, ai, c, null)) {
                    each += 15 * pow + 10 * tou;
                }
            }
            bonus += conditional ? each / 2 : each;
        }
        for (final Trigger t : c.getTriggers()) {
            if (t.getMode() == TriggerType.LifeGained && "You".equals(t.getParam("ValidPlayer"))) {
                bonus += 40;
            } else if (t.getMode() == TriggerType.ChangesZone && "Battlefield".equals(t.getParam("Destination"))
                    && t.hasParam("ValidCard") && t.getParam("ValidCard").contains("Other")
                    && t.getParam("ValidCard").contains("YouCtrl")) {
                bonus += 40;
            }
        }
        return bonus;
    }

    /**
     * What the gang loses: the most precious member the attacker can kill on its own (only one can die), or all of
     * them that it can kill when its damage covers several.
     */
    static int loss(final Player ai, final Card attacker, final Card a, final Card b) {
        final int dmg = attacker.getNetCombatDamage();
        final int absA = ComputerUtilCombat.getEnoughDamageToKill(a, dmg, attacker, true);
        final int absB = ComputerUtilCombat.getEnoughDamageToKill(b, dmg, attacker, true);
        final int wA = absA <= dmg ? worth(ai, a) : 0;
        final int wB = absB <= dmg ? worth(ai, b) : 0;
        return absA + absB > dmg ? Math.max(wA, wB) : wA + wB;
    }

    /**
     * Among the usable blockers, the pair that kills the attacker and loses the least, if it loses less than the
     * pair the stock AI chose ({@code leader} + {@code chosen}); null otherwise.
     */
    public static Card[] cheaperPair(final Player ai, final Combat combat, final Card attacker, final List<Card> usable,
            final Card leader, final Card chosen, final int damageNeeded) {
        int best = loss(ai, attacker, leader, chosen);
        Card[] pair = null;
        for (int i = 0; i < usable.size(); i++) {
            for (int j = i + 1; j < usable.size(); j++) {
                final Card a = usable.get(i);
                final Card b = usable.get(j);
                if (ComputerUtilCombat.totalDamageOfBlockers(attacker, List.of(a, b)) < damageNeeded
                        || !CombatUtil.canBlock(attacker, a, combat) || !CombatUtil.canBlock(attacker, b, combat)) {
                    continue;
                }
                final int dmg = attacker.getNetCombatDamage();
                // only one of them can die, as the stock condition asked
                if (ComputerUtilCombat.getEnoughDamageToKill(a, dmg, attacker, true)
                        + ComputerUtilCombat.getEnoughDamageToKill(b, dmg, attacker, true) <= dmg) {
                    continue;
                }
                final int l = loss(ai, attacker, a, b);
                if (l < best) {
                    best = l;
                    pair = new Card[] {a, b};
                }
            }
        }
        return pair;
    }

    private static int amount(final String s) {
        if (s == null) {
            return 0;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (final NumberFormatException e) {
            return 0;
        }
    }
}
