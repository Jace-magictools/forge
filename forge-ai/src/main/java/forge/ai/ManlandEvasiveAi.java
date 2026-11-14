package forge.ai;

import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.combat.CombatUtil;
import forge.game.player.Player;
import forge.game.spellability.AbilitySub;
import forge.game.spellability.SpellAbility;

/**
 * magic-tools: rule 244. "This becomes a creature, it can't be blocked this turn" (Creeping Tar Pit, Chromium, Dimir
 * Keyrune) writes the evasion as a sub-ability, an Effect the animated copy of AnimateAi does not carry: the AI judged
 * the attack as if it could be blocked and kept the land home, even with the opponent at 3 life and 8 lands untapped
 * (Solitaire, T22 to T26). An attacker no one can block risks nothing in combat: it attacks.
 */
public final class ManlandEvasiveAi {
    private ManlandEvasiveAi() { }

    /** the ability, further down its chain, makes its own host unblockable by every creature */
    static boolean makesSelfUnblockable(final SpellAbility sa) {
        final Card host = sa.getHostCard();
        for (AbilitySub sub = sa.getSubAbility(); sub != null; sub = sub.getSubAbility()) {
            if (sub.getApi() != ApiType.Effect || !"Self".equals(sub.getParam("RememberObjects"))
                    || !sub.hasParam("StaticAbilities")) {
                continue;
            }
            for (final String name : sub.getParam("StaticAbilities").split(",")) {
                final String st = host.getSVar(name.trim());
                if (st.contains("Mode$ CantBlockBy") && st.contains("ValidAttacker$ Card.IsRemembered")
                        && !st.contains("ValidBlocker$")) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean attacksUnblocked(final Player ai, final SpellAbility sa, final Card animatedCopy) {
        return animatedCopy.getNetCombatDamage() > 0 && makesSelfUnblockable(sa)
                && CombatUtil.canAttack(animatedCopy, ai.getWeakestOpponent());
    }
}
