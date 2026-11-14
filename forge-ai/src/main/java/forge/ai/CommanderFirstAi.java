package forge.ai;

import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 188 (Commander pods, 4/11). The spell sort goes by highest mana cost first, so a 3-mana commander
 * was passed over every turn for whatever bigger spell was in hand: The Scarlet Witch never cast in 10 turns with 6
 * Mountains, while it would have reduced the very spells cast instead. 3 commanders out of 32 never reached the table,
 * others came on turn 9 to 11. The first cast from the command zone costs no tax and is what the deck is built around:
 * it goes before the other spells of the turn, as long as the usual checks allow it. Later casts keep the usual order.
 */
public final class CommanderFirstAi {
    // above the gap between a commander and the biggest spell in hand, and above rituals and filters (+9)
    private static final int BONUS = 12;

    private CommanderFirstAi() { }

    public static int priority(final Player ai, final SpellAbility sa) {
        final Card source = sa.getHostCard();
        if (source == null || !sa.isSpell() || !source.isCommander() || !source.isInZone(ZoneType.Command)) {
            return 0;
        }
        return ai.getCommanderCast(source) == 0 ? BONUS : 0;
    }
}
