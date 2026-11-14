package forge.ai;

import java.util.List;

import forge.game.ability.AbilityUtils;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardLists;
import forge.game.keyword.Keyword;
import forge.game.player.Player;
import forge.game.staticability.StaticAbility;
import forge.game.staticability.StaticAbilityMode;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;

/**
 * magic-tools: rule 102 (a tester reports 30/10 12:20 to 12:29, Gobelin - Patrilock: Skullclamp never moved onto a
 * goblin token or Mogg War Marshal, four times). Forge's pump logic keeps an attachment off any creature its negative
 * toughness would kill ("don't kill my own stuff"). An attachment that rewards us when the creature it is attached to
 * dies (Skullclamp: draw two) wants exactly that creature: a cheap one of ours that dies from the attachment itself
 * (a token, or a nontoken of mana value 2 or less) is chosen first, the worst of them, and the equipment may move
 * again the same turn (each move is a fresh payoff).
 */
public final class DeathPayoffAttachAi {
    private DeathPayoffAttachAi() { }

    /** the attachment has a trigger "whenever the attached creature dies" */
    public static boolean paysOnDeath(final Card source) {
        for (final Trigger t : source.getTriggers()) {
            if (t.getMode() != TriggerType.ChangesZone || !"Battlefield".equals(t.getParam("Origin"))
                    || !"Graveyard".equals(t.getParam("Destination"))) {
                continue;
            }
            final String v = t.getParam("ValidCard");
            if (v != null && (v.contains("EquippedBy") || v.contains("EnchantedBy") || v.contains("AttachedBy"))) {
                return true;
            }
        }
        return false;
    }

    /** toughness the attachment gives to the creature it is attached to */
    public static int toughnessBonus(final Card source) {
        int tgh = 0;
        for (final StaticAbility st : source.getStaticAbilities()) {
            if (!st.checkMode(StaticAbilityMode.Continuous) || !st.hasParam("AddToughness")) {
                continue;
            }
            final String def = st.getParamOrDefault("AffectedDefined", "") + " " + st.getParamOrDefault("Affected", "");
            if (def.contains("Equipped") || def.contains("Enchanted") || def.contains("AttachedBy")) {
                tgh += AbilityUtils.calculateAmount(source, st.getParam("AddToughness"), st);
            }
        }
        return tgh;
    }

    /** our cheap creature that dies as soon as the attachment is on it */
    public static boolean isFodder(final Player ai, final Card source, final Card c) {
        if (c == null || !c.isCreature() || c.getController() != ai || c.equals(source.getAttachedTo())
                || !paysOnDeath(source)) {
            return false;
        }
        if (!c.isToken() && c.getCMC() > 2) {
            return false;
        }
        final int tgh = toughnessBonus(source);
        if (tgh >= 0) {
            return false;
        }
        if (c.getNetToughness() + tgh <= 0) {
            return true; // state-based, even indestructible
        }
        return c.getDamage() > 0 && c.getLethalDamage() <= -tgh && !c.hasKeyword(Keyword.INDESTRUCTIBLE);
    }

    /** the creature to attach to, or null when none dies from it */
    public static Card fodder(final Player ai, final Card source, final List<Card> list) {
        if (!paysOnDeath(source)) {
            return null;
        }
        final CardCollection dying = CardLists.filter(list, c -> isFodder(ai, source, c));
        return dying.isEmpty() ? null : ComputerUtilCard.getWorstCreatureAI(dying);
    }
}
