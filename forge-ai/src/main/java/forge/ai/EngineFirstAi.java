package forge.ai;

import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 14, the engine before its fuel. Kavaron held Weapons Manufacturing ("whenever a nontoken artifact
 * you control enters, create a Munitions") for 15 turns in 6 games against Giga, spending its mana on other 2-drops
 * and casting the artifacts first. General rule: a permanent in hand whose battlefield trigger "whenever a ... you
 * control enters" is fed by another nonland card in our hand is cast before the other spells of the same cost.
 * Rule 70 (a tester report 29/10, Héliode turn 1): a trigger that only gains life is not an engine worth the jump
 * (Case of the Uneaten Feast went before Guide of Souls, the 1/2 that attacks and makes energy).
 * Rule 75 (a tester report 29/10 12:59, Gwyll - Avacyn): the life does count when something uses it. Avacyn cast Serra
 * Avenger before Righteous Valkyrie with Ajani's Pridemate in play, losing the life and the counter. A life-only
 * trigger is an engine again when we control a "whenever you gain life" permanent, or when a permanent of ours or
 * the source itself has a static ability that depends on our life total (Valkyrie's +2/+2 at starting life + 7).
 * Rule 87 (a tester report 29/10 15:11, Chevalier - Gwyll): with Worthy Knight ("whenever you cast a Knight spell, create
 * a 1/1") in hand, the AI cast Rimrock Knight (same cost, can't block) from its adventure exile and lost the token.
 * A "whenever you cast a ... spell" trigger is an engine too, fed by another nonland card in hand or by a card we may
 * cast from exile (adventure, foretell...). A trigger that only untaps or pumps the source itself (Nettle Sentinel,
 * prowess-like) is not an engine: it would steal the turn order from the mana plan (rule 68).
 * Rule 88 (castFedBy): see BodyFirstAi, an aura waits for the engine it would feed.
 */
public final class EngineFirstAi {
    private EngineFirstAi() { }

    public static boolean fedFromHand(final Player ai, final Card source) {
        if (!source.isInZone(ZoneType.Hand) || !source.isPermanent()) {
            return false;
        }
        for (final Trigger t : source.getTriggers()) {
            if (castFed(ai, source, t)) {
                return true;
            }
            if (t.getMode() != TriggerType.ChangesZone || !"Battlefield".equals(t.getParam("Destination"))
                    || !"Battlefield".equals(t.getParam("TriggerZones")) || !t.hasParam("ValidCard")) {
                continue;
            }
            if (onlyGainsLife(source, t.getParam("Execute")) && !lifePayoff(ai, source)) {
                continue;
            }
            final String valid = t.getParam("ValidCard");
            if (!valid.contains("YouCtrl") || valid.contains("Self")) {
                continue;
            }
            final String[] restrictions = valid.split(",");
            for (final Card c : ai.getCardsIn(ZoneType.Hand)) {
                if (!c.equals(source) && !c.isLand() && c.isPermanent() && c.isValid(restrictions, ai, source, null)) {
                    return true;
                }
            }
        }
        return false;
    }

    // magic-tools: rule 87, "whenever you cast a ... spell" on the battlefield, fed by a later spell of ours
    private static boolean castFed(final Player ai, final Card source, final Trigger t) {
        if (!castEngine(ai, source, t)) {
            return false;
        }
        final String[] restrictions = t.getParam("ValidCard").split(",");
        final java.util.List<Card> fuel = new java.util.ArrayList<>(ai.getCardsIn(ZoneType.Hand));
        for (final Card c : ai.getCardsIn(ZoneType.Exile)) {
            if (!c.mayPlay(ai).isEmpty()) {
                fuel.add(c);
            }
        }
        for (final Card c : fuel) {
            if (!c.equals(source) && !c.isLand() && c.isValid(restrictions, ai, source, null)) {
                return true;
            }
        }
        return false;
    }

    // magic-tools: rule 88, the spell "fuel" would feed one of the engine's "whenever you cast" triggers
    public static boolean castFedBy(final Player ai, final Card engine, final Card fuel) {
        for (final Trigger t : engine.getTriggers()) {
            if (castEngine(ai, engine, t) && !fuel.equals(engine) && !fuel.isLand()
                    && fuel.isValid(t.getParam("ValidCard").split(","), ai, engine, null)) {
                return true;
            }
        }
        return false;
    }

    private static boolean castEngine(final Player ai, final Card source, final Trigger t) {
        if ((t.getMode() != TriggerType.SpellCast && t.getMode() != TriggerType.SpellCastOrCopy)
                || !"Battlefield".equals(t.getParam("TriggerZones")) || !"You".equals(t.getParam("ValidActivatingPlayer"))
                || !t.hasParam("ValidCard")) {
            return false;
        }
        if (onlyGainsLife(source, t.getParam("Execute")) && !lifePayoff(ai, source)) {
            return false;
        }
        final String exec = t.hasParam("Execute") ? source.getSVar(t.getParam("Execute")) : null;
        if (exec == null || (exec.matches("^(DB|AB|SP)\\$ ?(Untap|Pump|Tap)\\b.*") && exec.contains("Defined$ Self")
                && !exec.contains("SubAbility$"))) {
            return false;
        }
        return true;
    }

    // magic-tools: rule 70, every step of the trigger's ability (Execute, then its SubAbility chain) is GainLife
    public static boolean onlyGainsLife(final Card source, final String execute) {
        String name = execute;
        int depth = 0;
        while (name != null && depth++ < 10) {
            final String svar = source.getSVar(name);
            if (svar == null || !svar.matches("^(DB|AB|SP)\\$ ?GainLife\\b.*")) {
                return false;
            }
            name = null;
            for (final String part : svar.split("\\|")) {
                final String kv = part.trim();
                if (kv.startsWith("SubAbility$")) {
                    name = kv.substring("SubAbility$".length()).trim();
                }
            }
        }
        return depth > 0 && name == null;
    }

    // magic-tools: rule 75, something of ours turns the gained life into board (LifeGained trigger, life-total static)
    public static boolean lifePayoff(final Player ai, final Card source) {
        if (countsLife(source)) {
            return true;
        }
        for (final Card c : ai.getCardsIn(ZoneType.Battlefield)) {
            if (countsLife(c)) {
                return true;
            }
            for (final Trigger t : c.getTriggers()) {
                if (t.getMode() == TriggerType.LifeGained && "Battlefield".equals(t.getParam("TriggerZones"))
                        && (!t.hasParam("ValidPlayer") || "You".equals(t.getParam("ValidPlayer")))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean countsLife(final Card c) {
        for (final forge.game.staticability.StaticAbility st : c.getStaticAbilities()) {
            if (!st.hasParam("CheckSVar")) {
                continue;
            }
            final String sv = c.getSVar(st.getParam("CheckSVar"));
            if (sv != null && sv.contains("YourLifeTotal")) {
                return true;
            }
        }
        return false;
    }
}
