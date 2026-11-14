package forge.ai;

import forge.game.GameObject;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;

/**
 * magic-tools: rule 167 (relevé « mana perdu », visionneur v205, 04/11). Eldratron kept Sensei's Divining Top and two
 * Expedition Maps in hand for fifteen turns with eight untapped lands, under its own Culling Scales: rule 77 held
 * every permanent the Scales would destroy at our next upkeep. And when the Scales did target a Map, the AI let its
 * own trigger resolve without answering: Forge skips priority on its own stack object unless the ability is scripted
 * as a forced response.
 * General rule, two halves:
 * - an own spell or ability on top of the stack that threatens one of our permanents with an instant-speed
 *   activated ability (not a mana ability) is answered: the usual threatened-card logic (playImmediately,
 *   RespondToOwnActivation) then uses the permanent before it goes;
 * - a permanent spell is not held for fear of our own culler when it can use such an ability before the upkeep
 *   trigger resolves (its mana part payable with our lands).
 */
public final class OwnThreatResponseAi {
    private OwnThreatResponseAi() { }

    /** an activated, non-mana ability usable at instant speed, its mana part payable with maxMana. */
    public static boolean hasInstantAbility(final Card c, final int maxMana) {
        for (final SpellAbility sa : c.getSpellAbilities()) {
            if (!sa.isActivatedAbility() || sa.isManaAbility() || sa.isPwAbility()
                    || sa.getRestrictions().isSorcerySpeed() || sa.hasParam("ActivationPhases")
                    || sa.hasParam("PlayerTurn") || sa.hasParam("OpponentTurn")) {
                continue;
            }
            final int cost = sa.getPayCosts() == null || sa.getPayCosts().getTotalMana() == null ? 0
                    : sa.getPayCosts().getTotalMana().getCMC();
            if (cost <= maxMana) {
                return true;
            }
        }
        return false;
    }

    /** our own top of stack threatens one of our permanents that can still be used in response. */
    public static boolean mustAnswerOwn(final Player ai) {
        if (ai.getGame().getStack().isEmpty()) {
            return false;
        }
        final int mana = ComputerUtilMana.getAvailableManaEstimate(ai, false);
        for (final GameObject o : ComputerUtil.predictThreatenedObjects(ai, null, true)) {
            if (o instanceof Card c && c.isInPlay() && ai.equals(c.getController()) && hasInstantAbility(c, mana)) {
                return true;
            }
        }
        return false;
    }

    /** source is threatened by something on the stack (any controller), whatever the answering ability. */
    public static boolean isThreatened(final Player ai, final SpellAbility sa) {
        final Card host = sa.getHostCard();
        return host != null && host.isInPlay() && !ai.getGame().getStack().isEmpty()
                && ComputerUtil.predictThreatenedObjects(ai, null).contains(host);
    }
}
