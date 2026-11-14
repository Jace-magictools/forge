package forge.ai;

import forge.game.card.Card;
import forge.game.keyword.Keyword;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;

/**
 * magic-tools rule 287 (plateau D168, tester 11/11, a test game): Orque Berserk ("at the beginning
 * of your end step, if it is untapped, tap it and it deals damage equal to its power to you or another creature you
 * control") stayed home every turn and hit its controller for 3. Same family in the real cards: Erg Raiders ("if it
 * didn't attack this turn, it deals 2 damage to you"). Reads the script: a creature whose own end step trigger
 * (ValidPlayer You) is conditioned on itself being untapped or not having attacked, and whose effect deals damage to
 * or makes its controller lose life, attacks each combat like SVar:MustAttack. A vigilant creature stays untapped
 * after attacking: no gain for the "untapped" form, left alone.
 */
public final class EndStepPenaltyAi {
    private EndStepPenaltyAi() { }

    public static boolean mustAttack(final Card attacker) {
        for (final Trigger t : attacker.getTriggers()) {
            if (t.getMode() != TriggerType.Phase || !"End of Turn".equals(t.getParam("Phase"))
                    || !"You".equals(t.getParam("ValidPlayer")) || !t.hasParam("Execute")) {
                continue;
            }
            final String present = t.getParamOrDefault("IsPresent", "");
            if (!present.contains("Self")) {
                continue;
            }
            final boolean untapped = present.contains("untapped");
            final boolean notAttacked = present.contains("!attackedThisTurn");
            if (!untapped && !notAttacked || untapped && attacker.hasKeyword(Keyword.VIGILANCE)) {
                continue;
            }
            if (hurtsController(attacker, t.getParam("Execute"))) {
                return true;
            }
        }
        return false;
    }

    /** Follows the Execute / SubAbility chain of the script: damage or life loss aimed at "You". */
    private static boolean hurtsController(final Card c, String svar) {
        for (int depth = 0; svar != null && depth < 8; depth++) {
            final String effect = c.getSVar(svar);
            if (effect.isEmpty()) {
                return false;
            }
            final boolean harm = effect.contains("DB$ DealDamage") || effect.contains("DB$ LoseLife");
            if (harm && (aims(effect, "Defined$ You") || aims(effect, "ValidTgts$ You"))) {
                return true;
            }
            svar = param(effect, "SubAbility");
        }
        return false;
    }

    private static boolean aims(final String effect, final String key) {
        final int i = effect.indexOf(key);
        if (i < 0) {
            return false;
        }
        final int end = i + key.length();
        return end == effect.length() || effect.charAt(end) == ' ' || effect.charAt(end) == ',' || effect.charAt(end) == '|';
    }

    private static String param(final String effect, final String name) {
        for (final String part : effect.split("\\|")) {
            final String p = part.trim();
            if (p.startsWith(name + "$")) {
                return p.substring(name.length() + 1).trim();
            }
        }
        return null;
    }
}
