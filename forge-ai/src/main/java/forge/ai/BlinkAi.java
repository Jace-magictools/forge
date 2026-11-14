package forge.ai;

import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CounterEnumType;
import forge.game.player.Player;
import forge.game.spellability.AbilitySub;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;

/**
 * magic-tools, general rule 5 (tester, 24/10): a blink (Ephemerate, Charming Prince, Soulherder,
 * Eldrazi Displacer...) takes the creature whose enter-the-battlefield trigger is worth the most, minus
 * what it loses by leaving (counters, our auras and equipment). Forge took the best creature that has
 * any ETB trigger, even one that kills it (Phyrexian Dreadnought) or is worth nothing.
 */
public final class BlinkAi {
    private BlinkAi() { }

    /** Rough value of a creature's ETB triggers, if it entered again under our control. */
    public static int etbValue(final Player ai, final Card c) {
        int value = 0;
        for (final Trigger tr : c.getTriggers()) {
            if (tr.getMode() != TriggerType.ChangesZone
                    || !ZoneType.Battlefield.toString().equals(tr.getParam("Destination"))
                    || (tr.hasParam("ValidCard") && !tr.getParam("ValidCard").contains("Self"))) {
                continue;
            }
            // a trigger that would not fire on the new object (evoke's "Card.Self+evoked", unmet conditions)
            if ((tr.hasParam("ValidCard") && !c.isValid(tr.getParam("ValidCard").split(","), c.getController(), c, null))
                    || !tr.requirementsCheck(c.getGame())) {
                continue;
            }
            final SpellAbility sa = tr.ensureAbility();
            // magic-tools rule 313 (D177): a trigger whose required card target does not exist is removed from the
            // stack, nothing in its chain happens (Reflector Mage with no opposing creature)
            if (sa != null && NewAi.on(ai, 313) && noLegalCardTarget(ai, c, sa)) {
                continue;
            }
            if (sa != null) {
                value += chainValue(sa, 0);
            }
        }
        return value;
    }

    /** magic-tools rule 313: a required target among cards only (no player), and no card on the battlefield fits it. */
    static boolean noLegalCardTarget(final Player ai, final Card c, final SpellAbility sa) {
        final String valid = sa.getParam("ValidTgts");
        if (valid == null || "0".equals(sa.getParamOrDefault("TargetMin", "1"))
                || valid.contains("Player") || valid.contains("Any") || valid.contains("Opponent")
                || !ZoneType.Battlefield.toString().equals(sa.getParamOrDefault("TgtZone", "Battlefield"))) {
            return false;
        }
        try {
            final String[] v = valid.split(",");
            for (final Card t : c.getGame().getCardsIn(ZoneType.Battlefield)) {
                if (t != c && t.isValid(v, ai, c, sa)) {
                    return false;
                }
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * magic-tools rule 313 (D177, tester 13/11): what blinking one of our permanents gains, ETB or not: its arrival
     * minus what it loses, so a permanent without ETB still comes back to shed -1/-1 counters or an opposing aura.
     */
    public static int gain(final Player ai, final Card c) {
        return (c.hasETBTrigger(false) ? etbValue(ai, c) : 0) - leaveCost(ai, c);
    }

    private static int chainValue(final SpellAbility sa, final int depth) {
        if (sa == null || depth > 6) {
            return 0;
        }
        int value = effectValue(sa, depth);
        final AbilitySub sub = sa.getSubAbility();
        if (sub != null) {
            value += chainValue(sub, depth + 1);
        }
        return value;
    }

    private static int amount(final SpellAbility sa, final String param) {
        final String v = sa.getParam(param);
        if (v == null) {
            return 1;
        }
        try {
            return Math.max(1, Integer.parseInt(v));
        } catch (NumberFormatException e) {
            return 2;
        }
    }

    private static boolean hitsOpponent(final SpellAbility sa) {
        final String t = sa.getParamOrDefault("ValidTgts", "") + " " + sa.getParamOrDefault("Defined", "");
        return t.contains("Opp") || t.contains("Player") || t.contains("Any") || t.contains("Creature")
                || t.contains("Permanent") || t.contains("Artifact") || t.contains("Enchantment");
    }

    private static int effectValue(final SpellAbility sa, final int depth) {
        final ApiType api = sa.getApi();
        if (api == null) {
            return 0;
        }
        switch (api) {
            case Charm: {
                int best = 0;
                // the raw modes, not CharmEffect.makePossibleOptions: on a trigger that has not fired yet there is no
                // activating player, counting the targets of a mode threw a NullPointerException that cancelled the
                // whole priority (Charming Prince, Aether Channeler: Blink passed 300+ times a tournament)
                for (final AbilitySub choice : sa.getAdditionalAbilityList("Choices")) {
                    best = Math.max(best, chainValue(choice, depth + 1));
                }
                return best;
            }
            case Draw:
                return 60 * amount(sa, "NumCards");
            case Token:
                return 50 * amount(sa, "TokenAmount");
            case Destroy:
            case DestroyAll:
                return 90;
            case ChangeZone:
            case ChangeZoneAll: {
                final String origin = sa.getParamOrDefault("Origin", "");
                if (origin.contains("Battlefield")) {
                    return hitsOpponent(sa) ? 80 : 0;
                }
                if (origin.contains("Library") || origin.contains("Graveyard")) {
                    return 60;
                }
                return 20;
            }
            case DealDamage:
            case DamageAll:
                return 40;
            case GainControl:
                return 90;
            case Discard:
                return hitsOpponent(sa) ? 40 : -20;
            case Dig:
            case Scry:
            case Surveil:
            case Explore:
            case Mill:
                return 20;
            case PutCounter:
            case PutCounterAll:
            case Mana:
            case Untap:
            case Tap:
                return 15;
            case GainLife:
            case LoseLife:
            case Pump:
            case PumpAll:
                return 10;
            case Sacrifice:
                // no SacValid (or SacValid Self): the creature sacrifices itself (Phyrexian Dreadnought)
                if (!sa.hasParam("SacValid") || sa.getParam("SacValid").contains("Self")) {
                    return sa.hasParam("Defined") && !sa.getParam("Defined").contains("Self") ? 20 : -400;
                }
                return -40;
            case ChooseCard:
            case Cleanup:
            case StoreSVar:
            case Animate:
                return 0;
            default:
                return 20;
        }
    }

    /** What a creature loses by leaving: +1/+1 counters, our auras (to the graveyard), our equipment. */
    public static int leaveCost(final Player ai, final Card c) {
        int cost = 30 * c.getCounters(CounterEnumType.P1P1) - 30 * c.getCounters(CounterEnumType.M1M1);
        for (final Card a : c.getAttachedCards()) {
            if (a.getController().isOpponentOf(ai)) {
                cost -= 100; // Pacifism and friends fall off
            } else if (a.isAura()) {
                cost += 30 * Math.max(1, a.getCMC());
            } else {
                cost += 30;
            }
        }
        return cost;
    }

    /** Best of our creatures to blink, or null when none gains anything. */
    public static Card choose(final Player ai, final CardCollection own) {
        Card best = null;
        int bestValue = 0;
        for (final Card c : own) {
            final int v = etbValue(ai, c) - leaveCost(ai, c);
            if (v > bestValue || (v == bestValue && best != null && v > 0
                    && ComputerUtilCard.evaluateCreature(c) > ComputerUtilCard.evaluateCreature(best))) {
                best = c;
                bestValue = v;
            }
        }
        return best;
    }
}
