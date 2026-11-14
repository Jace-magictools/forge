package forge.ai;

import java.util.ArrayList;
import java.util.List;

import forge.game.card.Card;
import forge.game.combat.CombatUtil;
import forge.game.keyword.Keyword;
import forge.game.phase.PhaseHandler;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;

/**
 * magic-tools: rule 305 (D173, tester 12/11, a test game). With only Arbor Elf 1/1 and a 2/1 against two
 * 1/3, the AI cast Overwhelming Stampede (X = 2): one damage at best, the game-ending card wasted. A team pump until
 * end of turn cast from hand to attack (our turn, before attackers) is kept unless the attack it makes is lethal or
 * nearly (two life short at most, counting burn in hand), or it brings at least six more damage with three attackers
 * or more. Damage is estimated with each blocker stopping the attacker it costs us the most (a trampler only loses the
 * blocker's toughness).
 */
public final class MassPump305Ai {
    private MassPump305Ai() { }

    /** @return null when the rule has no opinion, else whether to cast the pump now. */
    public static Boolean castNow(final Player ai, final Player opp, final SpellAbility sa, final String valid,
            final int power, final List<String> keywords) {
        if (ai == null || opp == null || !NewAi.on(ai, 305) || sa.isCurse() || power <= 0 || !sa.isSpell()
                || sa.hasParam("Duration") || !valid.contains("YouCtrl")) {
            return null;
        }
        for (SpellAbility x = sa.getSubAbility(); x != null; x = x.getSubAbility()) {
            if (x.getApi() != sa.getApi()) {
                return null; // tokens, draw, untap: another reason to cast it
            }
        }
        final PhaseHandler ph = ai.getGame().getPhaseHandler();
        if (!ph.isPlayerTurn(ai) || !ph.getPhase().isBefore(PhaseType.COMBAT_DECLARE_ATTACKERS)
                || !opp.canLoseLife() || opp.cantLoseForZeroOrLessLife()) {
            return null;
        }
        final boolean haste = keywords.stream().anyMatch(k -> k.contains("Haste"));
        final boolean trample = keywords.stream().anyMatch(k -> k.contains("Trample"));
        final List<int[]> before = new ArrayList<>(); // {power, trample}
        final List<int[]> after = new ArrayList<>();
        for (final Card c : ai.getCreaturesInPlay()) {
            final boolean pumped = c.isValid(valid, ai, sa.getHostCard(), sa);
            final boolean ready = CombatUtil.canAttack(c, opp)
                    || (pumped && haste && CombatUtil.canAttackNextTurn(c, opp));
            if (!ready) {
                continue;
            }
            final int p = Math.max(0, c.getNetCombatDamage());
            final boolean t = c.hasKeyword(Keyword.TRAMPLE);
            if (CombatUtil.canAttack(c, opp)) {
                before.add(new int[] {p, t ? 1 : 0});
            }
            after.add(new int[] {pumped ? Math.max(0, p + power) : p, (t || (pumped && trample)) ? 1 : 0});
        }
        if (after.isEmpty()) {
            return null;
        }
        final List<Integer> blockers = new ArrayList<>();
        for (final Card b : opp.getCreaturesInPlay()) {
            if (CombatUtil.canBlock(b)) {
                blockers.add(Math.max(0, b.getNetToughness()));
            }
        }
        blockers.sort((a, b) -> b - a);
        final int dmgAfter = damage(after, blockers);
        final int gain = dmgAfter - damage(before, blockers);
        final int oppLife = opp.getLife() - ComputerUtil.possibleNonCombatDamage(ai, opp);
        if (dmgAfter + 2 >= oppLife) {
            return true;
        }
        return after.size() >= 3 && gain >= 6;
    }

    static int damage(final List<int[]> attackers, final List<Integer> blockers) {
        final List<int[]> left = new ArrayList<>();
        int total = 0;
        for (final int[] a : attackers) {
            left.add(a);
            total += a[0];
        }
        for (final int tough : blockers) {
            int best = -1;
            int loss = 0;
            for (int i = 0; i < left.size(); i++) {
                final int[] a = left.get(i);
                final int l = a[1] == 1 ? Math.min(a[0], tough) : a[0];
                if (l > loss) {
                    loss = l;
                    best = i;
                }
            }
            if (best < 0) {
                break;
            }
            left.remove(best);
            total -= loss;
        }
        return total;
    }
}
