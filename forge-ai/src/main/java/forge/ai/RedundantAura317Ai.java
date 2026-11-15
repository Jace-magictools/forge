package forge.ai;

import java.util.List;

import forge.game.card.Card;
import forge.game.card.CardLists;
import forge.game.player.Player;
import forge.game.staticability.StaticAbility;
import forge.game.staticability.StaticAbilityMode;

/**
 * magic-tools rule 317 (D197, plateau 15/11, a tester in a human game): the AI cast a second Unable to Scream
 * on Queza, Augur of Agonies, already a 0/2 without abilities under the first one. A curse aura that sets base
 * power/toughness or removes all abilities gains nothing on a creature that already carries such an aura, and
 * a curse aura with the same name gains nothing either unless it changes power/toughness (-X/-X stacks):
 * those creatures are dropped from the curse targets (another target, or the aura stays in hand).
 */
public final class RedundantAura317Ai {
    private RedundantAura317Ai() { }

    /** Static of an attachment that overwrites the attached creature (base P/T set, or all abilities lost). */
    static boolean overwrites(Card attachment) {
        for (StaticAbility st : attachment.getStaticAbilities()) {
            if (!st.checkMode(StaticAbilityMode.Continuous)) {
                continue;
            }
            String defined = st.getParam("AffectedDefined");
            String affected = st.getParam("Affected");
            boolean onAttached = (defined != null && (defined.equals("Enchanted") || defined.equals("Equipped")
                    || defined.startsWith("AttachedBy")))
                    || (affected != null && (affected.contains("EnchantedBy") || affected.contains("EquippedBy")
                    || affected.contains("AttachedBy")));
            if (onAttached && (st.hasParam("SetPower") || st.hasParam("SetToughness")
                    || st.hasParam("RemoveAllAbilities"))) {
                return true;
            }
        }
        return false;
    }

    private static boolean changesPT(Card attachment) {
        for (StaticAbility st : attachment.getStaticAbilities()) {
            if (st.checkMode(StaticAbilityMode.Continuous)
                    && (st.hasParam("AddPower") || st.hasParam("AddToughness"))) {
                return true;
            }
        }
        return false;
    }

    /** True when cursing c with source adds nothing. */
    static boolean redundant(Card source, Card c) {
        if (!c.isCreature()) {
            return false;
        }
        boolean over = overwrites(source);
        boolean pt = changesPT(source);
        for (Card a : c.getAttachedCards()) {
            if (a == source) {
                continue;
            }
            if (over && overwrites(a)) {
                return true;
            }
            if (!pt && a.getName().equals(source.getName())) {
                return true;
            }
        }
        return false;
    }

    public static List<Card> filter(Player ai, Card source, List<Card> list) {
        if (ai == null || source == null || !NewAi.on(ai, 317)) {
            return list;
        }
        return CardLists.filter(list, c -> !redundant(source, c));
    }
}
