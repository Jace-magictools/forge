package forge.ai;

import forge.game.cost.Cost;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;

/**
 * magic-tools rule 315 (D181, plateau 14/11, tester, a test game): Pisteur Uruk-Hai
 * "{X}{R}{B}: X target other creatures you control gain landwalk" was activated with X=0, no target, several
 * times. An ability whose X is paid ({X} in its mana cost, Count$xPaid) and whose number of targets is X
 * (TargetMax$ X) does nothing at X=0: the AI chooses X before targeting (at most the X it can pay and the
 * number of legal targets), lowers it while the effect AI refuses, and never activates it at X=0. X is then
 * the number of targets chosen.
 */
public final class XTargets315Ai {
    private static final int MAX_TRIES = 6;

    private XTargets315Ai() { }

    static boolean applies(Player ai, SpellAbility sa) {
        if (ai == null || sa == null || !NewAi.on(ai, 315)) {
            return false;
        }
        if (!sa.isAbility() && !sa.isSpell()) {
            return false;
        }
        if (!sa.usesTargeting() || !"X".equals(sa.getParam("TargetMax"))
                || !"Count$xPaid".equals(sa.getSVar("X"))) {
            return false;
        }
        Cost cost = sa.getPayCosts();
        return cost != null && cost.getTotalMana() != null && cost.getTotalMana().countX() > 0;
    }

    /** True when the effect AI must keep the X chosen here (PumpAi resets it otherwise). */
    public static boolean keepX(Player ai, SpellAbility sa) {
        SpellAbility root = sa.getRootAbility();
        return root == sa && applies(ai, root) && root.getXManaCostPaid() != null && root.getXManaCostPaid() > 0;
    }

    /** Replaces the effect AI call for such abilities; null when the rule does not apply. */
    static Boolean canPlay(Player ai, SpellAbility sa) {
        if (!applies(ai, sa)) {
            return null;
        }
        int x = ComputerUtilCost.setMaxXValue(sa, ai, false);
        int candidates = sa.getTargetRestrictions().getNumCandidates(sa);
        x = Math.min(x, candidates);
        for (int tries = 0; x >= 1 && tries < MAX_TRIES; x--, tries++) {
            sa.resetTargets();
            sa.setXManaCostPaid(x);
            if (SpellApiToAi.Converter.get(sa).canPlayWithSubs(ai, sa).willingToPlay()
                    && !sa.getTargets().isEmpty() && sa.isTargetNumberValid()) {
                sa.setXManaCostPaid(sa.getTargets().size());
                return Boolean.TRUE;
            }
        }
        sa.resetTargets();
        sa.setXManaCostPaid(null);
        return Boolean.FALSE;
    }
}
