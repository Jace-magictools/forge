package forge.ai;

import forge.game.Game;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardLists;
import forge.game.card.CardUtil;
import forge.game.card.CounterEnumType;
import forge.game.combat.Combat;
import forge.game.cost.CostSacrifice;
import forge.game.keyword.Keyword;
import forge.game.phase.PhaseHandler;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 243 (tester 06/11, a test game, tour 12: « sacrifier l'Experte avec Enjambeur, pour
 * qu'elle revienne et fasse defausser »). A free outlet eats a creature that comes straight back (Luminous Broodmoth,
 * Nightmare Shepherd, undying) when it replays a useful enter trigger: the sacrifice costs nothing and the trigger is
 * a free card. Read on the shapes of the scripts, no card name. It cannot loop: the creature comes back flying, as a
 * token or with a +1/+1 counter, none of which qualifies again. Persist stays out, the -1/-1 counter is a real cost.
 */
public final class SacRebuyAi {
    private SacRebuyAi() { }

    /** Play this outlet now: it can eat a creature that comes back and replays something useful. */
    public static boolean rebuys(final Player ai, final SpellAbility sa) {
        return ai.getGame().getStack().isEmpty() && best(ai, sa, null) != null;
    }

    /** The creature that pays the outlet under this rule, among the legal ones. */
    public static Card chooseFodder(final Player ai, final SpellAbility sa, final CardCollection typeList) {
        return best(ai, sa, typeList);
    }

    private static Card best(final Player ai, final SpellAbility sa, final CardCollection among) {
        final CostSacrifice sac = AristocratAi.freeSacCost(sa);
        if (sac == null) {
            return null;
        }
        final CardCollection ok = CardLists.filter(among != null ? among : AristocratAi.fodder(ai, sa, sac),
                c -> qualifies(ai, c));
        return ok.isEmpty() ? null : ComputerUtilCard.getWorstAI(ok);
    }

    static boolean qualifies(final Player ai, final Card c) {
        final Combat combat = ai.getGame().getCombat();
        if (c.isToken() || (combat != null && combat.isAttacking(c)) || c.isEnchanted() || c.isEquipped()
                || c.getCounters(CounterEnumType.P1P1) > 0) {
            return false;
        }
        final PhaseHandler ph = ai.getGame().getPhaseHandler();
        // on our turn, a creature that can still attack does it first: it comes back summoning sick
        if (ph.isPlayerTurn(ai) && ph.getPhase().isBefore(PhaseType.MAIN2) && !c.isSick()) {
            return false;
        }
        final boolean undying = c.hasKeyword(Keyword.UNDYING);
        return (undying || PersistLoopAi.returnedOnDeath(ai, c)) && usefulOnEnter(ai, c);
    }

    /** An unconditional enter trigger of the creature does something useful right now. */
    static boolean usefulOnEnter(final Player ai, final Card c) {
        final Game game = ai.getGame();
        for (final Trigger t : c.getTriggers()) {
            final String origin = t.getParamOrDefault("Origin", "Any");
            if (t.getMode() != TriggerType.ChangesZone || !"Battlefield".equals(t.getParam("Destination"))
                    || !"Card.Self".equals(t.getParam("ValidCard")) || t.hasParam("Condition")
                    || !("Any".equals(origin) || "Graveyard".equals(origin)) || !t.requirementsCheck(game)) {
                continue;
            }
            final SpellAbility ab = t.ensureAbility();
            if (ab != null && !ab.hasParam("UnlessCost") && !ab.hasParam("ConditionPresent")
                    && !ab.hasParam("ConditionCheckSVar") && useful(ai, c, ab)) {
                return true;
            }
        }
        return false;
    }

    private static boolean useful(final Player ai, final Card c, final SpellAbility ab) {
        final ApiType api = ab.getApi();
        if (api == null) {
            return false;
        }
        switch (api) {
            case Draw:
                return !ab.usesTargeting() && !ab.hasParam("Defined") && ai.getCardsIn(ZoneType.Library).size() > 2;
            case Token:
                return !ab.hasParam("TokenOwner");
            case Discard:
                final Player opp = ai.getWeakestOpponent();
                return opp != null && !opp.getCardsIn(ZoneType.Hand).isEmpty()
                        && (ab.usesTargeting() ? ab.getTargetRestrictions().canTgtPlayer()
                                : ab.getParamOrDefault("Defined", "").contains("Opponent"));
            case DealDamage:
            case LoseLife:
                return PersistLoopAi.dealsDamageOnEnter(c);
            case Destroy:
                return ab.usesTargeting() && hitsOpponent(ai, ab);
            default:
                return false;
        }
    }

    private static boolean hitsOpponent(final Player ai, final SpellAbility ab) {
        ab.setActivatingPlayer(ai);
        return !CardLists.filter(CardUtil.getValidCardsToTarget(ab), x -> x.getController().isOpponentOf(ai)).isEmpty();
    }
}
