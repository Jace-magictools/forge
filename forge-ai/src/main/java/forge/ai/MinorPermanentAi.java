package forge.ai;

import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardLists;
import forge.game.cost.CostPart;
import forge.game.cost.CostSacrifice;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 194 (a tester report 2/11 14:57). Lost to Legend, kept by a human for a threat, went on Chromatic
 * Star: a spell spent on a permanent that only exists to be used up. A removal spell (not an activated ability, not a
 * mandatory effect) no longer targets such a permanent; it waits in hand for a real target.
 * Minor: a noncreature, nonland, non-planeswalker permanent of mana value 2 or less (tokens included: Treasure, Clue,
 * Food) with no continuous or other static effect, whose abilities all sacrifice it for mana, a card, a land from the
 * library, life or a scry, and whose only trigger is the one for its own death. Sol Ring (mana without a sacrifice),
 * Oblivion Stone (destroys the board) and Pacifism (static effect) stay targets.
 */
public final class MinorPermanentAi {
    private MinorPermanentAi() { }

    public static CardCollection withoutMinor(final SpellAbility sa, final CardCollection list) {
        if (!sa.isSpell() || sa.isTrigger()) {
            return list;
        }
        return CardLists.filter(list, c -> !isMinor(c));
    }

    public static boolean isMinor(final Card c) {
        if (c.isCreature() || c.isLand() || c.isPlaneswalker() || c.getCMC() > 2 || c.isEnchanted()
                || !c.getStaticAbilities().isEmpty()) {
            return false;
        }
        for (final Trigger t : c.getTriggers()) {
            if (t.getMode() != TriggerType.ChangesZone
                    || !ZoneType.Battlefield.name().equals(t.getParam("Origin"))
                    || !ZoneType.Graveyard.name().equals(t.getParam("Destination"))) {
                return false;
            }
        }
        boolean anyAbility = false;
        for (final SpellAbility a : c.getAllSpellAbilities()) {
            if (!a.isActivatedAbility()) {
                continue;
            }
            anyAbility = true;
            if (!sacrificesItself(a) || !minorEffect(a)) {
                return false;
            }
        }
        return anyAbility;
    }

    private static boolean sacrificesItself(final SpellAbility a) {
        if (a.getPayCosts() == null) {
            return false;
        }
        for (final CostPart part : a.getPayCosts().getCostParts()) {
            if (part instanceof CostSacrifice && part.payCostFromSource()) {
                return true;
            }
        }
        return false;
    }

    private static boolean minorEffect(final SpellAbility a) {
        final ApiType api = a.getApi();
        if (api == ApiType.ChangeZone) {
            return ZoneType.Library.name().equals(a.getParam("Origin"));
        }
        return api == ApiType.Mana || api == ApiType.Draw || api == ApiType.GainLife || api == ApiType.Scry;
    }
}
