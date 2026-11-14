package forge.ai;

import java.util.EnumSet;

import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardCollectionView;
import forge.game.card.CardLists;
import forge.game.card.CounterEnumType;
import forge.game.cost.Cost;
import forge.game.player.Player;
import forge.game.keyword.Keyword;
import forge.game.spellability.SpellAbility;
import forge.game.staticability.StaticAbility;
import forge.game.staticability.StaticAbilityCantPutCounter;
import forge.game.staticability.StaticAbilityMode;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;

/**
 * magic-tools: general sacrifice rule. With the Default profile (SACRIFICE_DEFAULT_PREF_ENABLE=false), a
 * "Sacrifice a creature" cost without AIPreference was never paid: Goblin Bombardment, Altar of Dementia,
 * Viscera Seer... sat on the battlefield. Pay with what is worth less than the effect:
 * - free fodder, always: undying/persist ready, stolen until end of turn, useless creatures;
 * - cheap fodder, unless the outlet is free and minor (scry, surveil, pump, counter: repeatable for nothing):
 *   small creature tokens, non-mana non-creature tokens, cards that come back from the graveyard by themselves.
 * The worst card of the cheapest tier is chosen.
 * A creature that comes back (undying/persist) is not free while an opponent's permanent triggers on creatures
 * entering or leaving (Soul Warden, Blood Artist): each loop would feed the opponent.
 * Rule 10: nor while the piece that would make it loop (Melira for persist) is still in hand or library: a persist
 * creature spent on scry 1 comes back with its -1/-1 counter and can never loop (Pod lost Redcap that way).
 */
public final class FodderAi {
    private FodderAi() { }

    /** Below this (about a 2/2 token), a creature token is worth less than a card. */
    public static final int TOKEN_THRESHOLD = 160;

    private static final EnumSet<ApiType> MINOR = EnumSet.of(ApiType.Scry, ApiType.Surveil, ApiType.Pump,
            ApiType.PumpAll, ApiType.PutCounter);

    public static boolean isFree(final Player ai, final Card c) {
        return (ComputerUtilCard.hasActiveUndyingOrPersist(c) && !feedsOpponent(ai, c) && !returnSavedForLoop(ai, c))
                || ThreatenSacAi.isStolenUntilEot(ai, c)
                || (c.isCreature() && ComputerUtilCard.isUselessCreature(ai, c));
    }

    /**
     * the card would come back (persist/undying), the counter it comes back with is not prevented yet, and a card in our
     * hand or library prevents it (Melira, Sylvok Outcast): keep the return for the loop
     */
    public static boolean returnSavedForLoop(final Player ai, final Card c) {
        final CounterEnumType type;
        if (c.isToken()) {
            return false;
        } else if (c.hasKeyword(Keyword.PERSIST) && c.getCounters(CounterEnumType.M1M1) == 0) {
            type = CounterEnumType.M1M1;
        } else if (c.hasKeyword(Keyword.UNDYING) && c.getCounters(CounterEnumType.P1P1) == 0) {
            type = CounterEnumType.P1P1;
        } else {
            return false;
        }
        if (!c.canReceiveCounters(type)) {
            return false; // the enabler is already in play: the loop logic decides
        }
        for (final Card e : ai.getCardsIn(EnumSet.of(ZoneType.Hand, ZoneType.Library))) {
            if (e.equals(c)) {
                continue;
            }
            for (final StaticAbility st : e.getStaticAbilities()) {
                if (st.checkMode(StaticAbilityMode.CantPutCounter) && st.hasParam("ValidCard")
                        && st.getParam("ValidCard").startsWith("Creature")
                        && StaticAbilityCantPutCounter.applyCantPutCounter(st, c, type)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** an opponent's permanent triggers when this card dies or enters the battlefield (Soul Warden, Blood Artist) */
    public static boolean feedsOpponent(final Player ai, final Card c) {
        for (final Player opp : ai.getOpponents()) {
            for (final Card p : opp.getCardsIn(ZoneType.Battlefield)) {
                for (final Trigger t : p.getTriggers()) {
                    if (t.getMode() != TriggerType.ChangesZone) {
                        continue;
                    }
                    final boolean enters = "Battlefield".equals(t.getParam("Destination"));
                    final boolean leaves = "Battlefield".equals(t.getParam("Origin"));
                    if (!enters && !leaves) {
                        continue;
                    }
                    if (!t.hasParam("ValidCard")
                            || c.isValid(t.getParam("ValidCard").split(","), p.getController(), p, null)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean isCheap(final Player ai, final Card c) {
        if (c.isToken()) {
            if (c.isCreature()) {
                return ComputerUtilCard.evaluateCreature(c) < TOKEN_THRESHOLD;
            }
            return !c.isLand() && c.getManaAbilities().isEmpty();
        }
        return returnsFromGraveyard(c);
    }

    /** Bloodghast, Cauldron Familiar, Gravecrawler-like: an ability or trigger that works from the graveyard */
    public static boolean returnsFromGraveyard(final Card c) {
        for (final SpellAbility sa : c.getSpellAbilities()) {
            if ("Graveyard".equals(sa.getParamOrDefault("ActivationZone", "")) && sa.getApi() == ApiType.ChangeZone) {
                return true;
            }
        }
        for (final Trigger t : c.getTriggers()) {
            if (t.getParamOrDefault("TriggerZones", "").contains("Graveyard")) {
                return true;
            }
        }
        return false;
    }

    /** the outlet costs nothing but the sacrifice and does little: only free fodder goes */
    static boolean minorFreeOutlet(final SpellAbility sa) {
        if (sa == null || sa.getApi() == null || !MINOR.contains(sa.getApi())) {
            return false;
        }
        final Cost cost = sa.getPayCosts();
        return cost == null || (!cost.hasManaCost() && !cost.hasTapCost());
    }

    /** the card to pay a sacrifice cost with, or null if everything is worth keeping */
    public static Card choose(final Player ai, final CardCollectionView typeList, final SpellAbility sa) {
        final Card host = sa == null ? null : sa.getHostCard();
        final CardCollection free = CardLists.filter(typeList, c -> !c.equals(host) && isFree(ai, c));
        if (!free.isEmpty()) {
            return ComputerUtilCard.getWorstAI(free);
        }
        if (minorFreeOutlet(sa)) {
            return null;
        }
        // rule 81: a cheap creature that could still block waits for the opponent's combat (Altar of Dementia)
        final CardCollection cheap = CardLists.filter(typeList, c -> !c.equals(host) && isCheap(ai, c)
                && !BlockerFodderAi.keepForBlocking(ai, c, sa)
                && (!BlockerFodderAi.killsItsAttacker(ai, c) || BlockerFodderAi.winsNow(ai, c, sa)));
        final CardCollection blocking = CardLists.filter(cheap, c -> BlockerFodderAi.isBlockingNow(ai, c));
        if (!blocking.isEmpty()) {
            return ComputerUtilCard.getWorstAI(blocking);
        }
        if (!cheap.isEmpty()) {
            return ComputerUtilCard.getWorstAI(cheap);
        }
        return null;
    }
}
