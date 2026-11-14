package forge.ai;

import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 272 (tester 09/11 12:19, a test game turn 3): with one blue mana left in main 2,
 * the AI cast Merfolk Secretkeeper (0/4) instead of its adventure Venture Deeper (mill 4), while nothing threatened
 * it. General rule: the adventure half of a card in hand goes before its own permanent half when our life is not in
 * danger next combat: the card then gives both halves (the permanent is cast later from exile). If the adventure's
 * own AI refuses it (no target, wrong timing), the permanent half is still tried next.
 */
public final class AdventureFirst272 {
    private AdventureFirst272() { }

    public static int priority(final Player ai, final SpellAbility sa) {
        final Card source = sa.getHostCard();
        if (!NewAi.on(ai, 272) || !sa.isSpell() || !sa.isAdventure() || source == null
                || !source.isInZone(ZoneType.Hand)) {
            return 0;
        }
        if (ComputerUtil.aiLifeInDanger(ai, false, 0)) {
            return 0;
        }
        return 2;
    }
}
