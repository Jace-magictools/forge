package forge.ai;

import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 174 (forge-v212 replays, Infection against Poudlar and NRJ). Infection (twenty creatures, five
 * counterspells) held Corrupted Resolve for turns with two lands and never cast Plague Stinger, Blighted Agent or
 * Ichorclaw Myr: rule 22 kept the counterspell's mana open on its own turn.
 * General rule: rule 22 is for decks built around their counterspells (Transfuge). A deck whose cheap creatures
 * outnumber its counterspells two to one plays its creatures first: while it has no creature on the battlefield, a
 * creature spell is not held back for a counterspell.
 */
public final class ProactiveDeckAi {
    private ProactiveDeckAi() { }

    /** {@code host} is a creature the deck should cast now rather than keep counterspell mana open. */
    public static boolean deploysFirst(final Player ai, final Card host) {
        if (!host.isCreature() || !ai.getCreaturesInPlay().isEmpty()) {
            return false;
        }
        int cheapCreatures = 0;
        int counters = 0;
        for (final Card c : ai.getAllCards()) {
            if (c.isToken() || c.isLand() || !c.getOwner().equals(ai)) {
                continue;
            }
            if (CounterReserveAi.isCounterspell(c)) {
                counters++;
            } else if (c.isCreature() && c.getCMC() < CounterReserveAi.TAP_OUT_CMC) {
                cheapCreatures++;
            }
        }
        return counters > 0 && cheapCreatures >= 2 * counters;
    }
}
