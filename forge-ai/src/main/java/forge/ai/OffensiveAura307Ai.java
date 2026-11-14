package forge.ai;

import java.util.List;

import forge.game.card.Card;
import forge.game.card.CardLists;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;

/**
 * magic-tools: rule 307 (D171 point 3, tester 12/11, plateau): Rancor (+2/+0, trample) went on Arbor Elf, a mana
 * creature that taps for mana and never attacks, while bigger attackers stood next to it. Forge picks the "best"
 * creature by value, and the mana ability adds to the value. General rule for every aura or equipment that only adds
 * power and combat keywords (no granted ability, no toughness): when some candidate is a real attacker (can attack,
 * no {T} mana ability, no defender), the mana creatures and the non-attackers leave the list.
 */
public final class OffensiveAura307Ai {
    private OffensiveAura307Ai() { }

    /** power bonus, no toughness and no granted ability: a pure attack boost */
    public static boolean isOffensive(final Card attachSource, final int totPower, final int totToughness, final boolean grantingAbilities) {
        if (grantingAbilities || totPower <= 0 || totToughness > 0) {
            return false;
        }
        return attachSource.isAura() || attachSource.isEquipment();
    }

    static boolean tapsForMana(final Card c) {
        for (final SpellAbility ab : c.getManaAbilities()) {
            if (ab.getPayCosts() != null && ab.getPayCosts().hasTapCost()) {
                return true;
            }
        }
        // untappers like Arbor Elf: an ability with {T} that is not a mana ability but feeds mana
        for (final SpellAbility ab : c.getSpellAbilities()) {
            if (ab.isAbility() && ab.getPayCosts() != null && ab.getPayCosts().hasTapCost()
                    && ab.getApi() == forge.game.ability.ApiType.Untap) {
                return true;
            }
        }
        return false;
    }

    static boolean attacker(final Player ai, final Card c) {
        return c.isCreature() && c.getController().equals(ai) && !tapsForMana(c)
                && ComputerUtilCombat.canAttackNextTurn(c);
    }

    /** the list without mana creatures and non-attackers, when a real attacker remains; else the list unchanged */
    public static List<Card> filter(final Player ai, final Card attachSource, final List<Card> prefList,
            final int totPower, final int totToughness, final boolean grantingAbilities) {
        if (!NewAi.on(ai, 307) || !isOffensive(attachSource, totPower, totToughness, grantingAbilities)) {
            return prefList;
        }
        final List<Card> attackers = CardLists.filter(prefList, c -> attacker(ai, c));
        return attackers.isEmpty() ? prefList : attackers;
    }
}
