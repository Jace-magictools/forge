package forge.ai;

import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/**
 * magic-tools: Isochron Scepter (Patrilock). Stock AI imprinted its worst instant (generic "exile from hand")
 * and only activated the Scepter on an empty stack, even with a counterspell under it, sometimes for nothing.
 * Any useful instant goes under the Scepter, a counterspell first (tester, 24/10).
 */
public final class ScepterAi {
    private ScepterAi() { }

    /**
     * magic-tools, rule 124: set while the AI weighs the free copy of a Scepter's imprinted card. That copy costs no
     * card and comes back every turn, so removal is not kept for a better target (useRemovalNow): v18 tournament,
     * Jilt under the Scepter, 0 activation in ~58 turns.
     */
    private static final ThreadLocal<Boolean> FREE_COPY = ThreadLocal.withInitial(() -> Boolean.FALSE);

    public static boolean isFreeCopy() {
        return FREE_COPY.get();
    }

    public static void setFreeCopy(final boolean b) {
        FREE_COPY.set(b);
    }

    /** The host replays its imprinted card (Play ability over Card.IsImprinted). */
    public static boolean isScepter(final Card host) {
        if (host == null) {
            return false;
        }
        for (SpellAbility ab : host.getSpellAbilities()) {
            if (ab.getApi() == ApiType.Play && ab.getParamOrDefault("Valid", "").contains("IsImprinted")) {
                return true;
            }
        }
        return false;
    }

    /** Imprint trigger of a Scepter: exile an instant from hand. */
    public static boolean isScepterImprint(final SpellAbility sa) {
        return sa.hasParam("Imprint") && "Hand".equals(sa.getParam("Origin"))
                && "Exile".equals(sa.getParam("Destination")) && isScepter(sa.getHostCard());
    }

    public static Card chooseImprint(final Iterable<Card> options) {
        Card best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Card c : options) {
            int s = score(c);
            if (s > bestScore) {
                bestScore = s;
                best = c;
            }
        }
        return best;
    }

    /** Repeatable value: counter, then removal, then card flow, then the rest. */
    static int score(final Card c) {
        int best = 0;
        for (SpellAbility sp : c.getSpells()) {
            best = Math.max(best, score(sp));
        }
        return best * 10 + c.getCMC();
    }

    private static int score(final SpellAbility sp) {
        final ApiType api = sp.getApi();
        if (api == null) {
            return 1;
        }
        switch (api) {
            case Counter:
                return 10;
            case Destroy:
            case DealDamage:
                return 8;
            case ChangeZone:
                return sp.usesTargeting() && sp.getParamOrDefault("Origin", "").contains("Battlefield") ? 8 : 3;
            case Draw:
            case Dig:
                return 6;
            case Pump:
            case PumpAll:
            case Protection:
            case Tap:
                return 4;
            default:
                return 2;
        }
    }

    /**
     * magic-tools, rule 124: any spell that targets a spell (Counter, Reprieve / Remand-style ChangeZone from the
     * Stack) is a response; v18 tournament: Reprieve under the Scepter, 0 activation in ~50 turns.
     */
    static boolean answersSpell(final SpellAbility sp) {
        if (sp.getApi() == ApiType.Counter) {
            return true;
        }
        return sp.usesTargeting() && (sp.getParamOrDefault("TgtZone", "").contains("Stack")
                || sp.getParamOrDefault("Origin", "").equals("Stack"));
    }

    /** A counterspell under the Scepter: it is used in response to a spell, not only on an empty stack. */
    public static boolean imprintsCounter(final Card host) {
        if (!isScepter(host)) {
            return false;
        }
        for (Card c : host.getImprintedCards()) {
            if (c.isInZone(ZoneType.Exile)) {
                for (SpellAbility sp : c.getSpells()) {
                    if (answersSpell(sp)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
