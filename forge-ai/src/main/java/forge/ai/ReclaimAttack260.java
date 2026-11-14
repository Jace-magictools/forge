package forge.ai;

import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.spellability.TargetRestrictions;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 260 (D147 point 2, tester 08/11, a test game): two Phelia, Exuberant Shepherd stayed
 * home while Control Magic held our Alpine Watchdog and Igneous Cur. Her attack trigger exiles a permanent and returns
 * it at the end step under its OWNER's control: attacking takes the stolen creature back, even if she dies in the
 * combat (the trigger resolves before blocks, the stolen creature is gone before it can block). General rule: a
 * creature whose own attack trigger blinks a target (exile, then back to the battlefield, delayed or not) attacks when
 * a permanent we own and the opponent controls is a legal target worth at least as much as the attacker.
 */
public final class ReclaimAttack260 {
    private ReclaimAttack260() { }

    /** the best permanent of ours that the opponent controls and that this attack trigger would take back, or null */
    public static Card reclaimTarget(final Player ai, final Card attacker) {
        for (final Trigger t : attacker.getTriggers()) {
            if (t.getMode() != TriggerType.Attacks || !"Card.Self".equals(t.getParam("ValidCard"))) {
                continue;
            }
            final SpellAbility sa = t.ensureAbility();
            if (!isBlink(sa)) {
                continue;
            }
            final TargetRestrictions tr = sa.getTargetRestrictions();
            if (tr == null) {
                continue;
            }
            Card best = null;
            int bestValue = -1;
            for (final Card c : ai.getGame().getCardsIn(ZoneType.Battlefield)) {
                if (c == attacker || c.isToken() || !c.getOwner().equals(ai) || !c.getController().isOpponentOf(ai)) {
                    continue;
                }
                if (!c.isValid(tr.getValidTgts(), ai, attacker, sa) || !c.canBeTargetedBy(sa)) {
                    continue;
                }
                final int v = value(c);
                if (v > bestValue) {
                    best = c;
                    bestValue = v;
                }
            }
            if (best != null) {
                return best;
            }
        }
        return null;
    }

    /** true when the attack takes back something worth at least the attacker, so losing the attacker is acceptable */
    public static boolean worthTheRisk(final Player ai, final Card attacker) {
        final Card back = reclaimTarget(ai, attacker);
        if (back == null) {
            return false;
        }
        if (back.isCreature()) {
            return ComputerUtilCard.evaluateCreature(back) >= ComputerUtilCard.evaluateCreature(attacker);
        }
        return back.getCMC() >= attacker.getCMC();
    }

    private static int value(final Card c) {
        return c.isCreature() ? ComputerUtilCard.evaluateCreature(c) : 100 + 40 * c.getCMC();
    }

    /** exile from the battlefield, then a return: a delayed trigger (Phelia, Flickerwisp) or an immediate ChangeZone */
    private static boolean isBlink(final SpellAbility sa) {
        if (sa == null || sa.getApi() != ApiType.ChangeZone || !sa.usesTargeting()
                || !"Battlefield".equals(sa.getParam("Origin")) || !"Exile".equals(sa.getParam("Destination"))) {
            return false;
        }
        final SpellAbility sub = sa.getSubAbility();
        if (sub == null) {
            return false;
        }
        if (sub.getApi() == ApiType.DelayedTrigger) {
            // the return must not keep the card for the attacker's controller (« under your control »)
            final String exec = sub.hasParam("Execute") ? sa.getHostCard().getSVar(sub.getParam("Execute")) : "";
            return !exec.contains("GainControl");
        }
        return sub.getApi() == ApiType.ChangeZone && "Battlefield".equals(sub.getParam("Destination"))
                && !sub.hasParam("GainControl");
    }
}
