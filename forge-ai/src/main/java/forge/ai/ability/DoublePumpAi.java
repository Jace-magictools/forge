package forge.ai.ability;

import forge.game.ability.AbilityUtils;
import forge.game.card.Card;
import forge.game.spellability.SpellAbility;

/**
 * magic-tools: rule 189. NumAtt$ Double / Triple (Bulk Up, Unleash Fury, Dragonclaw Strike...) is resolved by
 * PumpEffect from the target's own stats, but the AI read it as an SVar: +0/+0, so these spells were never cast.
 */
public final class DoublePumpAi {
    private DoublePumpAi() {
    }

    private static boolean isMultiplier(final String amount) {
        return "Double".equals(amount) || "Triple".equals(amount);
    }

    /** The fixed part of the bonus: 0 for a multiplier, which depends on the target. */
    public static int fixedAmount(final SpellAbility sa, final String amount) {
        return isMultiplier(amount) ? 0 : AbilityUtils.calculateAmount(sa.getHostCard(), amount, sa);
    }

    private static int bonus(final String amount, final int stat, final int fixed) {
        if (!isMultiplier(amount)) {
            return fixed;
        }
        return Math.max(stat, 0) * ("Triple".equals(amount) ? 2 : 1);
    }

    public static int attackOn(final SpellAbility sa, final Card c, final int attack) {
        return bonus(sa.getParam("NumAtt"), c.getNetPower(), attack);
    }

    public static int defenseOn(final SpellAbility sa, final Card c, final int defense) {
        return bonus(sa.getParam("NumDef"), c.getNetToughness(), defense);
    }
}
