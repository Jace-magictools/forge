package forge.ai;

import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardLists;
import forge.game.card.CardUtil;
import forge.game.phase.PhaseHandler;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 127, mass clones (Mirrorform : "each nonland permanent you control becomes a copy of
 * target non-Aura permanent"). CloneAi refused every targeted clone without AILogic, so the card stayed
 * in hand (merfolk, 43 games in hand, 0 cast). A permanent clone whose CloneTarget is a group of our own
 * permanents is cast when the best creature target (non legendary, not a fatal copy) gains at least
 * MIN_GAIN in evaluation summed over the copied permanents, and turns at least two of them.
 * Timing: our first main phase (copies keep their sickness state, they can attack) or the opponent's end step.
 * Rule 168: the target is judged on what a copy gets (copiable values): its counters and pump effects do not
 * copy (v206, merfolk turned an 8/8 board into 0/1 Plant tokens: the Plant was 5/6 from +1/+1 counters).
 */
public final class MassCloneAi {
    private MassCloneAi() { }

    static final int MIN_GAIN = 250;

    public static boolean applies(final SpellAbility sa) {
        return sa.getApi() == ApiType.Clone && sa.usesTargeting() && !sa.hasParam("Duration")
                && !sa.hasParam("GainThisAbility") && sa.hasParam("CloneTarget")
                && sa.getParam("CloneTarget").startsWith("Valid ") && sa.getParam("CloneTarget").contains("YouCtrl");
    }

    public static boolean phaseOk(final Player ai, final PhaseHandler ph) {
        return ph.is(PhaseType.MAIN1, ai) || (ph.is(PhaseType.END_OF_TURN) && ph.getPlayerTurn().isOpponentOf(ai));
    }

    static int value(final Card c) {
        return c.isCreature() ? ComputerUtilCard.evaluateCreature(c) : 100 + 20 * c.getCMC();
    }

    /** Value of a copy of c: its evaluation without the power/toughness it gets from counters and effects. */
    static int copyValue(final Card c) {
        final int extraP = Math.max(0, c.getNetPower() - c.getBasePower());
        final int extraT = Math.max(0, c.getNetToughness() - c.getBaseToughness());
        return ComputerUtilCard.evaluateCreature(c) - 15 * extraP - 10 * extraT;
    }

    /** Sum of what the group gains when it becomes copies of target, or Integer.MIN_VALUE if fewer than two change. */
    public static int gain(final Player ai, final SpellAbility sa, final Card target) {
        final Card host = sa.getHostCard();
        final String valid = sa.getParam("CloneTarget").substring("Valid ".length());
        CardCollection group = CardLists.getValidCards(ai.getGame().getCardsIn(ZoneType.Battlefield), valid, ai, host, sa);
        group.remove(target);
        group = CardLists.filter(group, c -> c.getController().equals(ai) && !c.getName().equals(target.getName()));
        if (group.size() < 2) {
            return Integer.MIN_VALUE;
        }
        final int t = copyValue(target);
        int sum = 0;
        for (final Card c : group) {
            sum += t - value(c);
        }
        return sum;
    }

    public static Card chooseTarget(final Player ai, final SpellAbility sa) {
        CardCollection targets = CardLists.filter(CardUtil.getValidCardsToTarget(sa),
                c -> c.isCreature() && !c.getType().isLegendary());
        targets = ComputerUtilCard.filterOutFatalCopies(targets, ai);
        Card best = null;
        int bestGain = MIN_GAIN - 1;
        for (final Card c : targets) {
            final int g = gain(ai, sa, c);
            if (g > bestGain) {
                bestGain = g;
                best = c;
            }
        }
        return best;
    }

    public static boolean setTarget(final Player ai, final SpellAbility sa) {
        sa.resetTargets();
        final Card target = chooseTarget(ai, sa);
        if (target == null) {
            return false;
        }
        sa.getTargets().add(target);
        return true;
    }
}
