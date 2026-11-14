package forge.ai;

import java.util.List;

import forge.game.ability.AbilityUtils;
import forge.game.ability.ApiType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;

/**
 * magic-tools: rule 26, an X damage spell that can kill the opponent with all the mana available
 * (Fireball, Banefire, Crater's Claws…) is cast before anything else. Stock AI sorts it by its mana
 * value without X (1 for Fireball) and spent 13 lands on creatures first (Elemental, zomb18, opponent at 11).
 * Rule 54: same for a burn spell with fixed damage that kills now (Magma Jet against an opponent at 2: Red Star
 * cast Chandra first, six turns in a row, and had no mana left for it).
 */
public final class LethalBurnAi {
    private LethalBurnAi() { }

    public static boolean isXBurn(final SpellAbility sa) {
        return sa.isSpell() && sa.getApi() == ApiType.DealDamage && "X".equals(sa.getParam("NumDmg"))
                && "Count$xPaid".equals(sa.getSVar("X")) && sa.usesTargeting()
                && sa.getTargetRestrictions().canTgtPlayer();
    }

    public static boolean isLethal(final Player ai, final SpellAbility sa) {
        if (!isXBurn(sa)) {
            return isFixedLethal(ai, sa);
        }
        final Player opp = ai.getWeakestOpponent();
        if (opp == null || opp.cantLoseForZeroOrLessLife() || !opp.canLoseLife() || !sa.canTarget(opp)) {
            return false;
        }
        final int x = ComputerUtilMana.determineLeftoverMana(sa, ai, false);
        return x > 0 && ComputerUtilCombat.predictDamageTo(opp, x, sa.getHostCard(), false) >= opp.getLife();
    }

    /** Rule 54: fixed damage at the weakest opponent's face is enough to kill him (rule 55: divided damage too). */
    static boolean isFixedLethal(final Player ai, final SpellAbility sa) {
        if (!sa.isSpell() || sa.getApi() != ApiType.DealDamage || !sa.hasParam("NumDmg") || !sa.usesTargeting()
                || !sa.getParam("NumDmg").matches("\\d+")) {
            return false;
        }
        return FaceBurnAi.killsNow(sa, ai.getWeakestOpponent(), AbilityUtils.calculateAmount(sa.getHostCard(), sa.getParam("NumDmg"), sa));
    }

    public static void prioritize(final Player ai, final List<SpellAbility> all) {
        if (!ai.getGame().getPhaseHandler().isPlayerTurn(ai) || !ai.getGame().getPhaseHandler().getPhase().isMain()) {
            return;
        }
        for (int i = 1; i < all.size(); i++) {
            if (isLethal(ai, all.get(i))) {
                all.add(0, all.remove(i));
                return;
            }
        }
    }
}
