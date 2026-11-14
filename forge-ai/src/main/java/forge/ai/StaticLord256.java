package forge.ai;

import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.staticability.StaticAbility;
import forge.game.staticability.StaticAbilityMode;

/**
 * magic-tools: rule 256 (D147 point 1, tester 08/11, a test game): Control Magic (AI) took Alpine
 * Watchdog 3/3 while Blade Historian ("attacking creatures you control have double strike") was next to it: the
 * creature evaluator only reads the card's own body and keywords, a lord / anchor static is worth nothing. General
 * rule: a continuous static of the creature that pumps or gives keywords to creatures its controller controls
 * (Affected ... YouCtrl, "attacking" counted as if they attacked) adds, for each such creature in play, the value of
 * what it gives: 15 per +1 power, 10 per +1 toughness, double strike 10 + 10 x power, any other keyword 10.
 * Used by every caller of evaluateCreature (steal, removal, sacrifice choices...).
 */
public final class StaticLord256 {
    private StaticLord256() { }

    private static int amount(final StaticAbility stAb, final String param) {
        if (!stAb.hasParam(param)) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(stAb.getParam(param).trim().replace("+", "")));
        } catch (final NumberFormatException e) {
            return 1; // X, Count$... : at least something
        }
    }

    static int value(final Card c) {
        final Player you = c.getController();
        if (you == null || c.getGame() == null) {
            return 0;
        }
        int total = 0;
        for (final StaticAbility stAb : c.getStaticAbilities()) {
            if (!stAb.checkMode(StaticAbilityMode.Continuous) || !stAb.hasParam("Affected")
                    || stAb.hasParam("AffectedDefined") || stAb.hasParam("GainControl")) {
                continue;
            }
            final String affected = stAb.getParam("Affected");
            if (!affected.contains("YouCtrl")) {
                continue;
            }
            final int pow = amount(stAb, "AddPower");
            final int tou = amount(stAb, "AddToughness");
            final String[] kws = stAb.hasParam("AddKeyword") ? stAb.getParam("AddKeyword").split(" & ") : new String[0];
            if (pow == 0 && tou == 0 && kws.length == 0) {
                continue;
            }
            final String[] valid = AttackStrike255.stripAttacking(affected).split(",");
            for (final Card other : you.getCreaturesInPlay()) {
                if (!other.isValid(valid, you, c, stAb)) {
                    continue;
                }
                total += 15 * pow + 10 * tou;
                for (final String k : kws) {
                    final String kw = k.trim();
                    if (other.hasKeyword(kw)) {
                        continue; // already has it (from itself or another static)
                    }
                    total += "Double Strike".equals(kw) ? 10 + 10 * Math.max(0, other.getNetCombatDamage()) : 10;
                }
            }
        }
        return total;
    }
}
