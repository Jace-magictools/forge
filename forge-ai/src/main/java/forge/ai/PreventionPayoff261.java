package forge.ai;

import java.util.List;

import forge.game.ability.ApiType;
import forge.game.player.Player;
import forge.game.replacement.ReplacementEffect;
import forge.game.spellability.SpellAbility;

/**
 * magic-tools: rule 261 (D154, combos-forge 08/11, bench temp/d441): rule 616.1, the controller of the damaged object
 * orders the damage replacements. With Vigor and Furnace of Rath, Vigor was applied first (first of the list): 1 damage
 * prevented, 1 counter, and the Walking Ballista loop gained nothing. When a replacement prevents the damage and pays
 * the decider for it (+1/+1 counters on the damaged object, life gained: Vigor, Purity...), the replacements that
 * increase the damage (Twice, Thrice, Plus: Furnace of Rath, Dictate of the Twin Gods...) are applied first. Otherwise
 * the stock order.
 */
public final class PreventionPayoff261 {
    private PreventionPayoff261() { }

    /** a prevention whose replacement pays its controller (counters on the target, life gained) */
    static boolean paysDecider(final Player ai, final ReplacementEffect re) {
        if (!re.hasParam("PreventionEffect") && !re.hasParam("Prevent")) {
            return false;
        }
        if (re.getHostCard() == null || !ai.equals(re.getHostCard().getController())) {
            return false;
        }
        for (SpellAbility sa = re.ensureAbility(); sa != null; sa = sa.getSubAbility()) {
            if (sa.getApi() == ApiType.PutCounter || sa.getApi() == ApiType.GainLife) {
                return true;
            }
        }
        return false;
    }

    /** a replacement that raises the amount of damage */
    static boolean increasesDamage(final ReplacementEffect re) {
        final SpellAbility sa = re.ensureAbility();
        if (sa == null || sa.getApi() != ApiType.ReplaceEffect || !"DamageAmount".equals(sa.getParam("VarName"))) {
            return false;
        }
        final String var = sa.getParam("VarValue");
        String value = var == null ? null : sa.getSVar(var);
        if (value == null || value.isEmpty()) {
            value = re.getHostCard().getSVar(var);
        }
        return value != null && (value.contains("/Twice") || value.contains("/Thrice") || value.contains("/Plus"));
    }

    /** rule 261: the damage increaser to apply before a paying prevention, or null */
    public static ReplacementEffect firstIncrease(final Player ai, final List<ReplacementEffect> list) {
        boolean payoff = false;
        ReplacementEffect increase = null;
        for (final ReplacementEffect re : list) {
            if (paysDecider(ai, re)) {
                payoff = true;
            } else if (increase == null && increasesDamage(re)) {
                increase = re;
            }
        }
        return payoff ? increase : null;
    }
}
