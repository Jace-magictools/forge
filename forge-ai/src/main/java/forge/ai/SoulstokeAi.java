package forge.ai;

import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardCollectionView;
import forge.game.card.CardLists;
import forge.game.phase.PhaseHandler;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;

/**
 * magic-tools: Incandescent Soulstoke (Cendreline) was marked AI:RemoveDeck:All: the AI would put any
 * elemental from its hand onto the battlefield, even one it could cast this turn, and lose it at the end step.
 * With AILogic$ Soulstoke it only does it in its first main phase, stack empty, for an elemental it cannot
 * cast this turn and that does something before being sacrificed: an ETB of its own, an "elemental enters"
 * trigger of ours (Champion of the Path, Risen Reef...), or at least 3 power to attack with haste.
 */
public final class SoulstokeAi {
    private SoulstokeAi() { }

    public static boolean isSoulstoke(final SpellAbility sa) {
        return "Soulstoke".equals(sa.getParam("AILogic"));
    }

    public static boolean shouldActivate(final Player ai, final SpellAbility sa) {
        final PhaseHandler ph = ai.getGame().getPhaseHandler();
        if (!ph.is(PhaseType.MAIN1, ai) || !ai.getGame().getStack().isEmpty()) {
            return false;
        }
        return best(ai, sa, candidates(ai, sa)) != null;
    }

    public static Card choose(final Player ai, final SpellAbility sa, final CardCollection fetchList) {
        return best(ai, sa, fetchList);
    }

    private static CardCollectionView candidates(final Player ai, final SpellAbility sa) {
        final String type = sa.getParamOrDefault("ChangeType", "Creature");
        return CardLists.getValidCards(ai.getCardsIn(ZoneType.Hand), type, ai, sa.getHostCard(), sa);
    }

    private static Card best(final Player ai, final SpellAbility sa, final CardCollectionView list) {
        // mana left once the ability ({1}{R}) is paid
        final int manaLeft = ComputerUtilMana.getAvailableManaEstimate(ai, false) - sa.getPayCosts().getTotalMana().getCMC();
        Card best = null;
        int bestValue = 0;
        for (final Card c : list) {
            if (!c.isCreature() || c.getCMC() <= manaLeft) {
                continue;
            }
            int value = 0;
            if (c.hasETBTrigger(false)) {
                value += 100;
            }
            if (ourEnterTriggers(ai, c)) {
                value += 80;
            }
            if (c.getNetPower() >= 3) {
                value += 20 * c.getNetPower();
            }
            if (value == 0) {
                continue;
            }
            value += c.getCMC();
            if (value > bestValue) {
                best = c;
                bestValue = value;
            }
        }
        return best;
    }

    /** a permanent of ours triggers when another creature enters (Champion of the Path, Risen Reef...) */
    private static boolean ourEnterTriggers(final Player ai, final Card entering) {
        for (final Card p : ai.getCardsIn(ZoneType.Battlefield)) {
            for (final Trigger t : p.getTriggers()) {
                if (t.getMode() != TriggerType.ChangesZone || !"Battlefield".equals(t.getParam("Destination"))) {
                    continue;
                }
                final String valid = t.getParamOrDefault("ValidCard", "");
                if (valid.contains("Self") || valid.isEmpty()) {
                    continue;
                }
                if (entering.isValid(valid.split(","), ai, p, null)) {
                    return true;
                }
            }
        }
        return false;
    }
}
