package forge.ai;

import forge.game.card.Card;
import forge.game.cost.Cost;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 104 (a tester report 29/10 19:14, Kavaron - Nothing Gulch): Kavaron paid Sol Ring with a red source
 * and kept Fountainport untapped, while the hand needed red for two spells out of three. Forge keeps any mana source
 * with a non-mana activated ability for last (+13 per ability), even when that ability cannot be paid this turn.
 * General rule: a non-mana ability with a mana cost only protects its source if the other untapped mana sources, once
 * the current cost is paid, can still pay it. Otherwise the source is scored as a plain mana source, and the usual
 * order (colorless before colored, colors the hand needs kept longer) picks it.
 */
public final class SpareSourceAi {

    private SpareSourceAi() {
    }

    /** True when the non-mana ability of this source is worth keeping the source untapped for. */
    public static boolean keepsSourceFor(final Card source, final SpellAbility ability, final SpellAbility paying) {
        Cost cost = ability.getPayCosts();
        int need = cost == null || cost.getTotalMana() == null ? 0 : cost.getTotalMana().getCMC();
        if (need == 0 || paying == null) {
            return true;
        }
        return spareMana(source.getController(), source, paying) >= need;
    }

    /** Mana left from the other untapped sources and the pool once the cost being paid is covered. */
    static int spareMana(final Player ai, final Card source, final SpellAbility paying) {
        int sources = ai.getManaPool().totalMana();
        for (Card c : ai.getCardsIn(ZoneType.Battlefield)) {
            if (c.equals(source) || c.isTapped() || c.getManaAbilities().isEmpty()) {
                continue;
            }
            if (c.isCreature() && c.hasSickness()) {
                continue;
            }
            sources++;
        }
        Cost paid = paying.getPayCosts();
        int spent = paid == null || paid.getTotalMana() == null ? 0 : paid.getTotalMana().getCMC();
        return sources - spent;
    }
}
