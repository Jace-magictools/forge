package forge.ai;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import forge.game.Game;
import forge.game.GameObject;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardLists;
import forge.game.card.CounterEnumType;
import forge.game.combat.Combat;
import forge.game.keyword.Keyword;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.spellability.SpellAbilityStackInstance;

/**
 * magic-tools: Undying Evil (Karlov, Penitence) is a pump that only grants undying. PumpAi answered
 * removal only with toughness, indestructible or hexproof, so the spell stayed in hand while its
 * target died. Grant undying to a creature about to die: on the stack (destroy, lethal damage,
 * -X/-X, including our own Crypt Rats or Pestilence) or in combat after blockers.
 */
public final class UndyingSaveAi {
    private UndyingSaveAi() { }

    private static final Set<ApiType> DIES = EnumSet.of(ApiType.Destroy, ApiType.DestroyAll,
            ApiType.DealDamage, ApiType.DamageAll, ApiType.Pump, ApiType.PumpAll);

    public static boolean grantsUndying(final List<String> keywords) {
        return keywords.contains("Undying");
    }

    /** The creature would come back if it died now with undying. */
    public static boolean wouldReturn(final Card c) {
        return c.isCreature() && !c.isToken() && !c.hasKeyword(Keyword.UNDYING)
                && c.getCounters(CounterEnumType.P1P1) == 0;
    }

    private static boolean threatenedOnStack(final Player ai, final Card c) {
        final Game game = ai.getGame();
        if (game.getStack().isEmpty()) {
            return false;
        }
        final SpellAbility top = game.getStack().peekAbility();
        if (top == null || !DIES.contains(top.getApi())) {
            return false;
        }
        final List<GameObject> threatened = ComputerUtil.predictThreatenedObjects(ai, null, true);
        return threatened.contains(c);
    }

    private static boolean diesInCombat(final Player ai, final Card c) {
        final Game game = ai.getGame();
        final Combat combat = game.getCombat();
        return combat != null && game.getPhaseHandler().is(PhaseType.COMBAT_DECLARE_BLOCKERS)
                && (combat.isAttacking(c) || combat.isBlocking(c))
                && ComputerUtilCombat.combatantWouldBeDestroyed(ai, c, combat);
    }

    public static boolean isUseful(final Player ai, final Card c) {
        return c.getController().equals(ai) && wouldReturn(c) && (threatenedOnStack(ai, c) || diesInCombat(ai, c));
    }

    /** magic-tools: Undying Malice (Animate, AILogic$ UndyingSave) returns the creature whatever its counters. */
    public static boolean isMalice(final SpellAbility sa) {
        return "UndyingSave".equals(sa.getParam("AILogic"));
    }

    /**
     * Undying Malice: our best nontoken creature that the top of the stack kills, or that dies in combat
     * after blockers, once per creature and turn.
     */
    public static boolean chooseMaliceTarget(final Player ai, final SpellAbility sa) {
        CardCollection list = CardLists.getTargetableCards(ai.getCreaturesInPlay(), sa);
        list = CardLists.filter(list, c -> !c.isToken()
                && !alreadyReturns(ai, c)
                && (threatenedOnStack(ai, c) || diesInCombat(ai, c)));
        if (list.isEmpty()) {
            return false;
        }
        ComputerUtilCard.sortByEvaluateCreature(list);
        sa.resetTargets();
        sa.getTargets().add(list.getFirst());
        return true;
    }

    /** Already given "return it to the battlefield" this turn, or targeted by such a spell on the stack. */
    private static boolean alreadyReturns(final Player ai, final Card c) {
        if (c.getTriggers().anyMatch(t -> t.toString().contains("return it to the battlefield"))) {
            return true;
        }
        for (final SpellAbilityStackInstance si : ai.getGame().getStack()) {
            final SpellAbility sa = si.getSpellAbility();
            if (sa != null && isMalice(sa) && sa.getTargets().contains(c)) {
                return true;
            }
        }
        return false;
    }

    /** Response on the stack: target our best creature that the top of the stack kills. */
    public static boolean chooseTarget(final Player ai, final SpellAbility sa) {
        CardCollection list = CardLists.getTargetableCards(ai.getCreaturesInPlay(), sa);
        list = CardLists.filter(list, c -> wouldReturn(c) && threatenedOnStack(ai, c));
        if (list.isEmpty()) {
            return false;
        }
        ComputerUtilCard.sortByEvaluateCreature(list);
        sa.resetTargets();
        sa.getTargets().add(list.getFirst());
        return true;
    }
}
