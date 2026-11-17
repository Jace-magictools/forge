package forge.ai;

import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;

/**
 * magic-tools: rule 318 (D206, a human game 16/11: the Eldrazi AI kept Nulldrifter in hand two turns and never
 * evoked it). Rule 63 lets the AI evoke a creature only when one of its "enters" triggers does something, but an
 * evoked spell is still cast: its "when you cast this spell" triggers (Nulldrifter draws two, 20 cards or so) are
 * the whole point of the evoke. General rule: a self cast trigger that the AI would run (no target needed, or a
 * legal target found) makes the evoke useful.
 */
public final class EvokeCastTrigger318 {
    private EvokeCastTrigger318() { }

    public static boolean usefulCastTrigger(final AiController aic, final Player ai, final Card card) {
        if (!NewAi.on(ai, 318)) {
            return false;
        }
        for (final Trigger tr : card.getTriggers()) {
            if (tr.getMode() != TriggerType.SpellCast) {
                continue;
            }
            final String valid = tr.getParam("ValidCard");
            if (valid == null || !valid.startsWith("Card.Self") || valid.contains("kicked") || valid.contains("Gift")) {
                continue;
            }
            if (tr.hasParam("ValidActivatingPlayer") && !tr.getParam("ValidActivatingPlayer").equals("You")) {
                continue;
            }
            SpellAbility exSA = tr.ensureAbility();
            if (exSA == null) {
                continue;
            }
            exSA = exSA.copy(ai);
            exSA.setTrigger(tr);
            if (!exSA.usesTargeting()) {
                return true;
            }
            if (aic.doTrigger(exSA, false) && exSA.getTargets().size() > 0) {
                return true;
            }
        }
        return false;
    }
}
