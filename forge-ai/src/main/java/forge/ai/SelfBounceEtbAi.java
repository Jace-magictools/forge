package forge.ai;

import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardLists;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;

/**
 * magic-tools rule 165: a permanent whose mandatory enters-the-battlefield trigger returns or sacrifices
 * "a <type> you control" is not cast when it is the only such permanent: it would bounce or sacrifice itself and
 * the mana is lost (Lieutenant Orque, "When this enters, return an Orc you control to its owner's hand", cast
 * with no other Orc on the plateau, 04/11). Stock Forge relies on a NeedsToPlay SVar written card by card
 * (Whitemane Lion has one, Dream Stalker and Sawtooth Loon do not). Left to stock: a trigger with a follow-up
 * effect or a card with another arrival trigger (rebuying it may be the point), "Self" or "Other" types
 * (Uro sacrifices itself on purpose), optional, targeted or opponent-chosen effects.
 * When another permanent of the type is there, the bounce picks it, not the card just cast (ChangeZoneAi).
 */
public final class SelfBounceEtbAi {
    private SelfBounceEtbAi() { }

    public static boolean onlyItself(final Card card, final SpellAbility trigger, final Player ai) {
        if (card == null || ai == null || trigger == null || !card.isPermanent()
                || trigger.getSubAbility() != null || trigger.usesTargeting()) {
            return false;
        }
        final String valid;
        final String num;
        if (trigger.getApi() == ApiType.ChangeZone) {
            if (!trigger.hasParam("Hidden") || !trigger.hasParam("Mandatory") || trigger.hasParam("DefinedPlayer")
                    || trigger.hasParam("Chooser") || !"Battlefield".equals(trigger.getParam("Origin"))) {
                return false;
            }
            valid = trigger.getParam("ChangeType");
            num = trigger.getParamOrDefault("ChangeNum", "1");
        } else if (trigger.getApi() == ApiType.Sacrifice) {
            if (trigger.hasParam("Optional") || trigger.hasParam("UnlessCost")
                    || (trigger.hasParam("Defined") && !"You".equals(trigger.getParam("Defined")))) {
                return false;
            }
            valid = trigger.getParamOrDefault("SacValid", "Self");
            num = trigger.getParamOrDefault("Amount", "1");
        } else {
            return false;
        }
        if (valid == null || valid.contains("Self") || valid.contains("Other") || !num.matches("\\d+")) {
            return false;
        }
        final String[] types = valid.split(",");
        if (!card.isValid(types, ai, card, trigger)) {
            return false;
        }
        CardCollection others = CardLists.getValidCards(ai.getCardsIn(ZoneType.Battlefield), types, ai, card, trigger);
        others.remove(card);
        if (others.size() >= Integer.parseInt(num)) {
            return false;
        }
        int arrivals = 0;
        for (final Trigger tr : card.getTriggers()) {
            if (tr.getMode() == TriggerType.ChangesZone && "Battlefield".equals(tr.getParam("Destination"))
                    && tr.getParamOrDefault("ValidCard", "").contains("Self")) {
                arrivals++;
            }
        }
        return arrivals == 1;
    }

    // the ability comes from the card's own "when this enters" trigger
    public static boolean isOwnArrivalTrigger(final SpellAbility sa) {
        final Trigger tr = sa.getRootAbility().getTrigger();
        return tr != null && tr.getMode() == TriggerType.ChangesZone && "Battlefield".equals(tr.getParam("Destination"))
                && tr.getParamOrDefault("ValidCard", "").contains("Self");
    }
}
