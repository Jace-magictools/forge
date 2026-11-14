package forge.ai;

import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 31, use the engine in play before casting another copy. Pod against Rohan (26/10) held three
 * Birthing Pods with Kitchen Finks in play: in main 2 the AI sorts by mana value, so the second Pod (4) went before
 * the first Pod's activation (2) and took the mana, three turns in a row. General rule: a non-legendary noncreature
 * permanent whose only abilities are tap abilities (no trigger, no static) is cast after the activated abilities when
 * an untapped copy of it is already ours: the copy adds nothing this turn, the activation does.
 */
public final class DuplicateEngineAi {
    private DuplicateEngineAi() { }

    public static boolean redundant(final Player ai, final SpellAbility sa) {
        final Card source = sa.getHostCard();
        if (!sa.isSpell() || source == null || !source.isInZone(ZoneType.Hand) || !source.isPermanent()
                || source.isCreature() || source.isLand() || source.getType().isLegendary()
                || !source.getTriggers().isEmpty() || !source.getStaticAbilities().isEmpty()) {
            return false;
        }
        boolean tapEngine = false;
        for (final SpellAbility ab : source.getSpellAbilities()) {
            if (!ab.isActivatedAbility() || ab.isManaAbility()) {
                continue;
            }
            if (ab.getPayCosts() == null || !ab.getPayCosts().hasTapCost()) {
                return false;
            }
            tapEngine = true;
        }
        if (!tapEngine) {
            return false;
        }
        return ai.getCardsIn(ZoneType.Battlefield).anyMatch(c -> c.getName().equals(source.getName()) && !c.isTapped());
    }
}
