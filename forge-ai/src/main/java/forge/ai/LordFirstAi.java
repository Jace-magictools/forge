package forge.ai;

import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.staticability.StaticAbility;
import forge.game.staticability.StaticAbilityMode;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 76 (a tester report 29/10 13:11, Gwyll - Deathbringer Liege). With Nip Gwyllion and Voracious
 * Hatchling (white and black hybrids) in play, the AI cast Obzedat before Deathbringer Liege (same cost): the Liege
 * would have given +2/+2 to each of them at once. General rule: a permanent spell whose static ability pumps the
 * creatures we control (Continuous, Affected ...YouCtrl, AddPower/AddToughness) gets a priority bonus that grows with
 * what it adds to the creatures already in play, in the same units as evaluateCreature (15 per power, 10 per
 * toughness), so a lord that pumps two creatures goes before a bigger body of the same cost.
 */
public final class LordFirstAi {
    private LordFirstAi() { }

    // what the static pumps add to our creatures already in play, in evaluateCreature points
    public static int boardBonus(final Player ai, final Card source) {
        if (!source.isInZone(ZoneType.Hand) || !source.isPermanent()) {
            return 0;
        }
        int bonus = 0;
        for (final StaticAbility st : source.getStaticAbilities()) {
            if (!st.getMode().contains(StaticAbilityMode.Continuous) || !st.hasParam("Affected")
                    || st.hasParam("CheckSVar") || st.hasParam("IsPresent") || st.hasParam("Condition")) {
                continue;
            }
            final String affected = st.getParam("Affected");
            if (!affected.contains("YouCtrl") || !affected.startsWith("Creature")) {
                continue;
            }
            final int pow = amount(st.getParam("AddPower"));
            final int tou = amount(st.getParam("AddToughness"));
            if (pow <= 0 && tou <= 0) {
                continue;
            }
            final String[] restrictions = affected.split(",");
            for (final Card c : ai.getCreaturesInPlay()) {
                if (!c.equals(source) && c.isValid(restrictions, ai, source, null)) {
                    bonus += 15 * Math.max(0, pow) + 10 * Math.max(0, tou);
                }
            }
        }
        return bonus;
    }

    // priority points for the spell sorter (same order of magnitude as the creature evaluation term)
    public static int priority(final Player ai, final SpellAbility sa) {
        final Card source = sa.getHostCard();
        if (source == null || !sa.isSpell()) {
            return 0;
        }
        return Math.min(9, Math.round(boardBonus(ai, source) / 15f));
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
