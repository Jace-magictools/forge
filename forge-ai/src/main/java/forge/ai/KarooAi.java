package forge.ai;

import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardLists;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 72, bounce lands ("karoo": Golgari Rot Farm, Simic Growth Chamber...). They enter tapped and
 * return a land you control to hand, so the land drop costs the mana of the returned land this turn unless it was
 * tapped first. Stock AI played the karoo first thing in main 1 and bounced an untapped Forest: Troll had Varolz
 * castable with Haunted Mire, Forest and Skull Prophet and cast nothing (a tester report 29/10 11:59). Here the
 * karoo waits for main 2 and for the spells castable now, and its trigger returns a tapped land when there is one
 * (its own mana is never available the turn it comes down, so holding it costs nothing).
 */
public final class KarooAi {
    private KarooAi() { }

    /** True if the land has an ETB trigger returning a land you control to its owner's hand. */
    public static boolean isKaroo(final Card land) {
        if (land == null || !land.isLand()) {
            return false;
        }
        for (final Trigger t : land.getTriggers()) {
            if (t.getMode() != TriggerType.ChangesZone) {
                continue;
            }
            if (!"Battlefield".equals(t.getParam("Destination")) || !"Card.Self".equals(t.getParam("ValidCard"))) {
                continue;
            }
            final SpellAbility exec = t.ensureAbility();
            if (exec == null) {
                continue;
            }
            if ("Battlefield".equals(exec.getParam("Origin")) && "Hand".equals(exec.getParam("Destination"))
                    && exec.getParamOrDefault("ChangeType", "").startsWith("Land")) {
                return true;
            }
        }
        return false;
    }

    /**
     * magic-tools: rule 137. The Ravnica karoos carry SVar:NeedsToPlay:Land.Basic+YouCtrl, so the stock AI never played
     * one without a basic land in play (tournament v21: BlastBurn held two Rakdos Carnarium for four turns next to
     * Maze of Ith and Dragonskull Summit; 303 lost land drops over 17 decks). Any other land we control can be returned
     * and replayed; only another karoo does not count (bouncing it gains nothing).
     */
    public static boolean hasLandToReturn(final Card karoo) {
        return karoo.getController().getCardsIn(ZoneType.Battlefield).anyMatch(
                c -> c.isLand() && !c.equals(karoo) && !isKaroo(c));
    }

    /** Among the lands the karoo may return, the tapped ones (their mana is already spent), if any. */
    public static CardCollection preferTapped(final CardCollection fetchList) {
        if (fetchList.isEmpty() || !fetchList.allMatch(c -> c.isLand() && c.isInZone(ZoneType.Battlefield))) {
            return fetchList;
        }
        final CardCollection tapped = CardLists.filter(fetchList, Card::isTapped);
        return tapped.isEmpty() ? fetchList : tapped;
    }
}
