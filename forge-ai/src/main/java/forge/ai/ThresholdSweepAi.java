package forge.ai;

import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CardCollectionView;
import forge.game.card.CardLists;
import forge.game.card.CounterEnumType;
import forge.game.cost.CostPayLife;
import forge.game.keyword.Keyword;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/**
 * magic-tools, rule 181 (general rule): a sweeper that destroys every permanent with mana value up to a number the
 * caster picks (X of Forced March, Gaze of Granite, Meltdown, Vicious Rivalry; the energy of Wrath of the Skies)
 * is played with the number that hurts the opponents most and us least, not the biggest one. Wrath of the Skies
 * hides its sweep behind an energy counter: it was never cast (socrate at 5 life, 7 lands, facing a 2/1 flier).
 */
public final class ThresholdSweepAi {
    private static final int MIN_LIFE_LEFT = 4;
    private static final double VALUE_OF_ONE_LIFE = 0.5;
    /** the margin of PumpAllAi's other sweepers: 200 points of creature evaluation */
    private static final double MIN_MINUS_X_GAIN = 200 / 30.0;

    private ThresholdSweepAi() { }

    private static boolean destroyable(final Card c) {
        return !(c.hasKeyword(Keyword.INDESTRUCTIBLE) || c.getCounters(CounterEnumType.SHIELD) > 0 || c.hasSVar("SacMe"));
    }

    private static double value(final CardCollectionView list) {
        double v = 0;
        for (final Card c : list) {
            if (!destroyable(c)) {
                continue;
            }
            v += c.isCreature() ? ComputerUtilCard.evaluateCreature(c) / 30.0 : c.getCMC() + 1;
        }
        return v;
    }

    /** opponents' loss minus ours for the sweep valid as written (its number already resolved) */
    public static double score(final Player ai, final SpellAbility sa, final String valid) {
        final Card src = sa.getHostCard();
        double opp = 0;
        for (final Player o : ai.getOpponents()) {
            opp += value(CardLists.getValidCards(o.getCardsIn(ZoneType.Battlefield), valid, ai, src, sa));
        }
        return opp - value(CardLists.getValidCards(ai.getCardsIn(ZoneType.Battlefield), valid, ai, src, sa));
    }

    /** the threshold in 0..max that wins the most (smallest on a tie), or -1 if none helps us */
    public static int best(final Player ai, final SpellAbility sa, final String valid, final String var, final int max) {
        int best = -1;
        double bestScore = 0;
        for (int t = 0; t <= max; t++) {
            final double s = score(ai, sa, valid.replace("cmcLE" + var, "cmcLE" + t));
            if (s > bestScore + 1e-9) {
                best = t;
                bestScore = s;
            }
        }
        return best;
    }

    /**
     * magic-tools, rule 191: "all creatures get -X/-X" with X chosen by the caster (Toxic Deluge, Scavenger Regent).
     * X was read as 0 when deciding, so nothing seemed to die and the spell was never cast.
     */
    public static boolean isMinusXSweep(final SpellAbility sa) {
        return "-X".equals(sa.getParam("NumDef")) && "Count$xPaid".equals(sa.getSVar("X"))
                && !sa.isTrigger() && sa.getRootAbility() == sa;
    }

    private static double valueKilled(final CardCollectionView list, final int x) {
        double v = 0;
        for (final Card c : list) {
            if (c.isCreature() && c.getNetToughness() <= x) {
                v += ComputerUtilCard.evaluateCreature(c) / 30.0;
            }
        }
        return v;
    }

    /** the X worth paying (opponents' creatures killed minus ours minus the life paid), or -1 if none is */
    public static int bestMinusX(final Player ai, final SpellAbility sa, final String valid) {
        final Card src = sa.getHostCard();
        int max = ComputerUtilCost.setMaxXValue(sa, ai, false);
        final CostPayLife life = sa.getPayCosts().getCostPartByType(CostPayLife.class);
        final boolean paysLife = life != null && "X".equals(life.getAmount());
        if (paysLife) {
            max = Math.min(max, ai.getLife() - MIN_LIFE_LEFT);
        }
        final CardCollectionView ours = CardLists.getValidCards(ai.getCardsIn(ZoneType.Battlefield), valid, ai, src, sa);
        int best = -1;
        double bestScore = MIN_MINUS_X_GAIN;
        for (int x = 1; x <= max; x++) {
            double score = -valueKilled(ours, x) - (paysLife ? x * VALUE_OF_ONE_LIFE : 0);
            for (final Player o : ai.getOpponents()) {
                score += valueKilled(CardLists.getValidCards(o.getCardsIn(ZoneType.Battlefield), valid, ai, src, sa), x);
            }
            if (score > bestScore + 1e-9) {
                best = x;
                bestScore = score;
            }
        }
        return best;
    }

    /** DestroyAll paid with energy as an unless cost the caster chooses to pay (Wrath of the Skies) */
    public static boolean isEnergySweep(final SpellAbility sa) {
        return sa != null && sa.getApi() == ApiType.DestroyAll && sa.hasParam("UnlessSwitched")
                && sa.getParamOrDefault("UnlessCost", "").startsWith("PayEnergy")
                && sa.getParamOrDefault("ValidCards", "").contains("cmcLE");
    }

    /** the energy sweep further down the chain of sa, or null */
    public static SpellAbility energySweepIn(final SpellAbility sa) {
        for (SpellAbility sub = sa == null ? null : sa.getSubAbility(); sub != null; sub = sub.getSubAbility()) {
            if (isEnergySweep(sub)) {
                return sub;
            }
        }
        return null;
    }

    /** the threshold variable of the sweep (Y in cmcLEY), or null */
    public static String variable(final SpellAbility sweep) {
        final String valid = sweep.getParamOrDefault("ValidCards", "");
        final int i = valid.indexOf("cmcLE");
        return i < 0 || i + 6 > valid.length() ? null : valid.substring(i + 5, i + 6);
    }

    /** cast decision of the spell that gives the energy: X so that energy reaches the best threshold */
    public static AiAbilityDecision decideEnergySweep(final Player ai, final SpellAbility sa, final SpellAbility sweep) {
        final String var = variable(sweep);
        if (var == null) {
            return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
        }
        final int energy = ai.getCounters(CounterEnumType.ENERGY);
        final boolean xPaid = "Count$xPaid".equals(sa.getSVar("X"));
        final int maxX = xPaid ? ComputerUtilCost.setMaxXValue(sa, ai, sa.isTrigger()) : 0;
        final int t = best(ai, sweep, sweep.getParam("ValidCards"), var, energy + maxX);
        if (t < 0) {
            return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
        }
        if (xPaid) {
            sa.setXManaCostPaid(Math.max(0, t - energy));
        }
        return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
    }

    /** the number chosen for a ChooseNumber feeding an energy sweep, or -1 when sa is not one */
    public static int chooseNumber(final Player ai, final SpellAbility sa, final int min, final int max) {
        final SpellAbility sweep = isEnergySweep(sa.getSubAbility()) ? sa.getSubAbility() : null;
        if (sweep == null || variable(sweep) == null) {
            return -1;
        }
        final int t = best(ai, sweep, sweep.getParam("ValidCards"), variable(sweep), max);
        return Math.max(min, Math.min(max, t < 0 ? min : t));
    }
}
