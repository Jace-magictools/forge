package forge.ai;

import forge.game.card.Card;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/**
 * magic-tools rule 212 (tester 03/11, a test game, turn 8): stock Forge keeps an extra land in hand
 * when "an ability with a discard cost is present", and it looks through the graveyard without asking whether that ability
 * can be activated from there. A cycled Sheltering Landscape in the graveyard (cycling works from the hand only) kept the
 * eighth land in hand for nothing. Only an ability usable from the zone the card is in justifies keeping a land.
 */
public final class DiscardOutletAi {
    private DiscardOutletAi() {
    }

    public static boolean usableFromItsZone(final Card c, final SpellAbility sa) {
        if (sa.isSpell()) {
            // a spell is only castable from outside the hand with flashback and the like, or as a commander
            return sa.isFlashback() || (c.isCommander() && c.isInZone(ZoneType.Command));
        }
        final ZoneType zone = sa.getRestrictions().getZone();
        return zone == null || c.isInZone(zone);
    }
}
