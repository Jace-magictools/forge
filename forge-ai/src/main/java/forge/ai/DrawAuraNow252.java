package forge.ai;

import forge.game.ability.AbilityUtils;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.combat.CombatUtil;
import forge.game.phase.PhaseHandler;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;

/**
 * magic-tools: rule 252 (a tester report 08/11, D143 point 3, a test game turns 2-3): two Curiosity were
 * put on Soul-Scar Mage, the first while it was summoning sick, the second in the second main phase after it had
 * attacked. An aura that draws when the enchanted creature deals damage (AttachAILogic Curiosity: Curiosity, Snake
 * Umbra, Sixth Sense, Ophidian Eye…) is only put on a creature that can deal that damage this turn: before the
 * attacks, untapped, able to attack; or a pinger whose ability is ready (rule 233). And one copy per creature.
 */
public final class DrawAuraNow252 {
    private DrawAuraNow252() { }

    static boolean pinger(final Card card) {
        for (final SpellAbility sa : card.getSpellAbilities()) {
            if (ApiType.DealDamage.equals(sa.getApi()) && sa.usesTargeting() && sa.getTargetRestrictions().canTgtPlayer()
                    && sa.hasParam("NumDmg") && AbilityUtils.calculateAmount(card, sa.getParam("NumDmg"), sa) > 0) {
                return true;
            }
        }
        return false;
    }

    /** rule 252: can {@code card} deal damage to an opponent this turn, so that an aura put now draws at once? */
    public static boolean paysThisTurn(final Player ai, final Card card) {
        if (card.isTapped()) {
            return false;
        }
        if (pinger(card) && !card.isSick()) {
            return true;
        }
        final PhaseHandler ph = ai.getGame().getPhaseHandler();
        return ph.isPlayerTurn(ai) && ph.getPhase().isBefore(PhaseType.COMBAT_DECLARE_ATTACKERS)
                && card.getNetCombatDamage() > 0 && CombatUtil.canAttack(card);
    }

    /** rule 252: does {@code card} already wear an aura with the name of {@code source}? */
    public static boolean alreadyWears(final Card card, final Card source) {
        for (final Card a : card.getEnchantedBy()) {
            if (a != source && a.getName().equals(source.getName())) {
                return true;
            }
        }
        return false;
    }

    public static boolean skip(final Player ai, final Card card, final Card source, final boolean mandatory) {
        return !mandatory && ai != null && (alreadyWears(card, source) || !paysThisTurn(ai, card));
    }
}
