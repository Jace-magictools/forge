package forge.ai;

import forge.card.MagicColor;
import forge.card.mana.ManaCostShard;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardLists;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/**
 * magic-tools, rule 210 (tester 05/11, Pod a test game: "tu viens de sacrifier ta seule source de
 * mana noire, alors que tu as que des sorts noirs en main"): a creature that is our only source of a color does not pay
 * a sacrifice cost while a spell in hand cannot be cast without that color. A land in hand counts as a source.
 */
public final class SoleColorAi {
    private SoleColorAi() { }

    /** c is our only source of a color that a nonland card in hand needs */
    public static boolean keepsColor(final Player ai, final Card c) {
        if (!c.isCreature() || c.getManaAbilities().isEmpty()) {
            return false;
        }
        byte others = 0;
        for (final ZoneType zone : new ZoneType[] {ZoneType.Battlefield, ZoneType.Hand}) {
            for (final Card other : ai.getCardsIn(zone)) {
                if (other != c && (zone == ZoneType.Battlefield || other.isLand())) {
                    others |= colorsOf(other);
                }
            }
        }
        final byte lost = (byte) (colorsOf(c) & ~others);
        if (lost == 0) {
            return false;
        }
        for (final Card spell : ai.getCardsIn(ZoneType.Hand)) {
            if (!spell.isLand() && needs(spell, lost, others)) {
                return true;
            }
        }
        return false;
    }

    /** The sacrifice choices without the creatures to keep; all of them when nothing else can pay. */
    public static CardCollection withoutSoleSource(final Player ai, final CardCollection choices) {
        final CardCollection kept = CardLists.filter(choices, c -> !keepsColor(ai, c));
        return kept.isEmpty() ? choices : kept;
    }

    private static byte colorsOf(final Card c) {
        byte colors = 0;
        for (final SpellAbility ma : c.getManaAbilities()) {
            for (final byte color : MagicColor.WUBRG) {
                if (ma.canProduce(MagicColor.toShortString(color))) {
                    colors |= color;
                }
            }
        }
        return colors;
    }

    /** a colored symbol of the spell can only be paid with a lost color (a Phyrexian one is paid with life) */
    private static boolean needs(final Card spell, final byte lost, final byte others) {
        for (final ManaCostShard shard : spell.getManaCost()) {
            if (shard.isGeneric() || shard.isPhyrexian() || shard.isSnow() || shard.isColorless() || shard.isOr2Generic()) {
                continue;
            }
            if ((shard.getColorMask() & lost) != 0 && (shard.getColorMask() & others) == 0) {
                return true;
            }
        }
        return false;
    }
}
