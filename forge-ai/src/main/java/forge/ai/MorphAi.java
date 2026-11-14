package forge.ai;

import forge.card.CardStateName;
import forge.game.card.CardState;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;

/**
 * Morph and the like: casting a card face down costs {3} for a vanilla 2/2. The AI may do it only if the card
 * costs at least as much face up, or if turning it face up does something (Willbender...). Otherwise it casts
 * Birchlore Rangers face down and loses its mana ability.
 */
public final class MorphAi {
    private static final int FACE_DOWN_CMC = 3;

    private MorphAi() {
    }

    public static boolean worthCastingFaceDown(final SpellAbility sa) {
        if (!sa.isCastFaceDown()) {
            return true;
        }
        final CardState faceUp = sa.getHostCard().getState(CardStateName.Original);
        if (faceUp == null || faceUp.getManaCost().getCMC() >= FACE_DOWN_CMC) {
            return true;
        }
        for (Trigger t : faceUp.getTriggers()) {
            if (t.getMode() == TriggerType.TurnFaceUp) {
                return true;
            }
        }
        return false;
    }
}
