package forge.ai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardLists;
import forge.game.cost.Cost;
import forge.game.cost.CostExile;
import forge.game.cost.CostPart;
import forge.game.cost.CostPartMana;
import forge.game.cost.CostPayLife;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 26 (tester, 26/10), a spell dealing X damage divided evenly among its targets
 * (Fireball) is split only when every target still dies: X=2 kills two 1/1s (1+1), but X=1 on House-Mouse
 * and Anafenza dealt 0 to each (ana22). Killing the opponent comes first (LethalBurnAi, shouldTgtP); then the
 * split that kills the most creature value, each target beyond the first costing {1} more.
 */
public final class DividedBurnAi {
    private DividedBurnAi() { }

    /** magic-tools, rule 193: value of one life against a creature (same scale as ThresholdSweepAi) */
    private static final double VALUE_OF_ONE_LIFE = 0.5;
    private static final double CREATURE_SCALE = 30.0;
    /** the whole spell must be worth about one 200-point creature */
    private static final double MIN_GAIN = 200 / CREATURE_SCALE;

    /** mana plus one X paid in life or by exiling from the graveyard, and nothing else */
    public static boolean isLifeOrGraveX(final Cost cost) {
        int x = 0;
        for (final CostPart part : cost.getCostParts()) {
            if (part instanceof CostPartMana) {
                continue;
            }
            final boolean fromGrave = part instanceof CostExile exile && exile.from.contains(ZoneType.Graveyard);
            if (!"X".equals(part.getAmount()) || !(part instanceof CostPayLife || fromGrave)) {
                return false;
            }
            x++;
        }
        return x == 1;
    }

    /** Opponents' creatures killed with their share of damage, best gain first, within budget; empty when not worth it. */
    public static Map<Card, Integer> killsWorthTheirCost(final Player ai, final SpellAbility sa, final int budget,
            final boolean noPrevention) {
        final CardCollection candidates = new CardCollection();
        for (final Player opp : ai.getOpponents()) {
            candidates.addAll(CardLists.getTargetableCards(opp.getCreaturesInPlay(), sa));
        }
        final Map<Card, Integer> needs = new HashMap<>();
        for (final Card c : candidates) {
            needs.put(c, need(c, budget, sa, noPrevention));
        }
        candidates.sort((a, b) -> Double.compare(gain(b, needs.get(b)), gain(a, needs.get(a))));
        final Map<Card, Integer> kills = new LinkedHashMap<>();
        int left = budget;
        double total = 0;
        for (final Card c : candidates) {
            final int need = needs.get(c);
            if (need < 1 || need > left || gain(c, need) <= 0 || kills.size() >= sa.getMaxTargets()) {
                continue;
            }
            kills.put(c, need);
            left -= need;
            total += gain(c, need);
        }
        return total > MIN_GAIN ? kills : new LinkedHashMap<>();
    }

    private static double gain(final Card c, final int need) {
        return need == Integer.MAX_VALUE ? -1 : ComputerUtilCard.evaluateCreature(c) / CREATURE_SCALE - need * VALUE_OF_ONE_LIFE;
    }

    /** Damage needed to kill c, or a huge number if it can't be killed by damage now. */
    public static int need(final Card c, final int dmg, final SpellAbility sa, final boolean noPrevention) {
        if (c.getShieldCount() > 0 || ComputerUtil.canRegenerate(c.getController(), c)) {
            return Integer.MAX_VALUE;
        }
        return ComputerUtilCombat.getEnoughDamageToKill(c, dmg, sa.getHostCard(), false, noPrevention);
    }

    /**
     * Opponents' creatures to target, best split first; empty when no creature dies.
     * @param budget X with a single target (all leftover mana)
     */
    public static List<Card> chooseCreatures(final Player ai, final SpellAbility sa, final int budget,
            final boolean noPrevention) {
        final CardCollection candidates = new CardCollection();
        for (final Player opp : ai.getOpponents()) {
            candidates.addAll(CardLists.getTargetableCards(opp.getCreaturesInPlay(), sa));
        }
        ComputerUtilCard.sortByEvaluateCreature(candidates);
        List<Card> best = new ArrayList<>();
        int bestValue = 0;
        final int maxK = Math.min(candidates.size(), sa.getMaxTargets());
        for (int k = 1; k <= maxK; k++) {
            final int share = (budget - (k - 1)) / k;
            if (share <= 0) {
                break;
            }
            final List<Card> killed = new ArrayList<>();
            int value = 0;
            for (final Card c : candidates) {
                if (need(c, share, sa, noPrevention) <= share) {
                    killed.add(c);
                    value += ComputerUtilCard.evaluateCreature(c);
                    if (killed.size() == k) {
                        break;
                    }
                }
            }
            if (killed.size() == k && value > bestValue) {
                best = killed;
                bestValue = value;
            }
        }
        return best;
    }

    /** X to pay so that every chosen creature dies: the largest need times the number of targets. */
    public static int xToPay(final Iterable<Card> targets, final int dmg, final SpellAbility sa, final boolean noPrevention) {
        int maxNeed = 0;
        int n = 0;
        for (final Card c : targets) {
            n++;
            maxNeed = Math.max(maxNeed, Math.min(dmg, need(c, dmg, sa, noPrevention)));
        }
        return maxNeed * n;
    }
}
