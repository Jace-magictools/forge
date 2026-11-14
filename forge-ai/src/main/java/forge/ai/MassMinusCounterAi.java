package forge.ai;

import java.util.List;

import forge.game.card.Card;
import forge.game.card.CounterEnumType;
import forge.game.player.Player;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 176 (forge-v213 replays, Hapatra against Artob). With two Nest of Scarabs and Obelisk Spider in
 * play, Hapatra kept Soul Snuffers in hand for ten turns (4 to 8 lands open): "put a -1/-1 counter on each creature"
 * was only judged by the number of opposing creatures it kills (more than two), never by what it costs us or what our
 * own permanents earn from -1/-1 counters.
 * General rule: a "-1/-1 counter on each creature" effect is weighed on both sides: the opposing creatures it kills,
 * plus every counter our payoffs turn into value ("whenever you put one or more -1/-1 counters on a creature": a token,
 * a drain), against our own creatures it kills.
 */
public final class MassMinusCounterAi {
    /** Value of one payoff trigger (a 1/1 token or a 1-life drain), on the evaluateCreature scale. */
    static final int PAYOFF_VALUE = 80;

    private MassMinusCounterAi() { }

    /** Our permanents that trigger when we put -1/-1 counters on a creature. */
    public static int payoffs(final Player ai) {
        int n = 0;
        for (final Card c : ai.getCardsIn(ZoneType.Battlefield)) {
            for (final Trigger t : c.getTriggers()) {
                if ((t.getMode() == TriggerType.CounterAddedOnce || t.getMode() == TriggerType.CounterAdded)
                        && "M1M1".equals(t.getParam("CounterType"))
                        && t.getParamOrDefault("ValidCard", "Creature").contains("Creature")
                        && "You".equals(t.getParamOrDefault("ValidSource", "You"))) {
                    n++;
                }
            }
        }
        return n;
    }

    private static int killed(final List<Card> list, final int amount) {
        int v = 0;
        for (final Card c : list) {
            if (c.isCreature() && c.canReceiveCounters(CounterEnumType.M1M1) && c.getNetToughness() <= amount) {
                v += ComputerUtilCard.evaluateCreature(c);
            }
        }
        return v;
    }

    /** The effect gives us more than it takes from us. */
    public static boolean worthIt(final Player ai, final List<Card> ours, final List<Card> theirs, final int amount) {
        if (amount <= 0) {
            return false;
        }
        int receivers = 0;
        for (final Card c : ours) {
            if (c.canReceiveCounters(CounterEnumType.M1M1)) {
                receivers++;
            }
        }
        for (final Card c : theirs) {
            if (c.canReceiveCounters(CounterEnumType.M1M1)) {
                receivers++;
            }
        }
        final int gain = killed(theirs, amount) + payoffs(ai) * receivers * PAYOFF_VALUE;
        final int loss = killed(ours, amount);
        return gain > loss && gain >= PAYOFF_VALUE * 2;
    }
}
