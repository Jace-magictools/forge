package forge.ai;

import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardLists;
import forge.game.combat.CombatUtil;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;

/**
 * magic-tools: rule 92 (a tester report 29/10 12:20, Dauthi - ElfBall, turn 5): Vault Skirge (flying, lifelink) already
 * wore Ordeal of Erebos and carried Bonesplitter; Unholy Strength stayed in hand all game with a Swamp left open.
 * Forge never puts an aura on a creature that is already enchanted (fear of a two-for-one). General rule: when every
 * candidate is already enchanted, the aura still goes on one that no opposing creature can block (flying against a
 * board with no flyer or reach, shadow, unblockable...): that creature is the deck's plan, the extra power turns into
 * damage at once. Against a board that can block it, Forge keeps its caution.
 */
public final class StackAuraAi {
    private StackAuraAi() { }

    public static CardCollection unblockable(final Player ai, final CardCollection enchanted) {
        return CardLists.filter(enchanted, c -> c.isCreature() && c.getController().equals(ai) && !blockable(ai, c));
    }

    /**
     * magic-tools: rule 237 (relevé par lot, 6/11, Infect: Blight Mamba wearing a Rancor, two more Rancor in hand with
     * 6 lands open, turns 8 to 12). The fear of a two-for-one does not apply to an aura that returns to its owner's hand
     * when it goes to the graveyard (Rancor, Undying Rage, Fallen Ideal...): it may stack on an enchanted creature.
     */
    public static boolean comesBack(final Card aura) {
        for (final Trigger t : aura.getTriggers()) {
            if (t.getMode() != TriggerType.ChangesZone || !"Battlefield".equals(t.getParam("Origin"))
                    || !"Graveyard".equals(t.getParam("Destination")) || !"Card.Self".equals(t.getParam("ValidCard"))) {
                continue;
            }
            final SpellAbility back = t.ensureAbility();
            if (back != null && back.getApi() == ApiType.ChangeZone && "Hand".equals(back.getParam("Destination"))
                    && back.getParamOrDefault("Defined", "").startsWith("Triggered") && !back.usesTargeting()) {
                return true;
            }
        }
        return false;
    }

    private static boolean blockable(final Player ai, final Card attacker) {
        for (final Player opp : ai.getOpponents()) {
            for (final Card blocker : opp.getCreaturesInPlay()) {
                if (CombatUtil.canBlock(attacker, blocker, true)) {
                    return true;
                }
            }
        }
        return false;
    }
}
