package forge.ai;

import java.util.ArrayList;
import java.util.List;

import forge.game.ability.AbilityUtils;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.phase.PhaseHandler;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 309 (Sauron, relevés v335: the AI activates The One Ring at 7 then 6 life and dies of its own life
 * loss). Forge only checks that the next upkeep alone doesn't kill (life > burden + 1). General rule for a recurring
 * self life loss: before adding to it, the AI adds up every "at the beginning of your upkeep, you lose N life" of its
 * permanents (with the extra point), plus what the opponents' creatures on the battlefield can deal before that upkeep
 * (their attackers, minus as many of the biggest as we have untapped blockers). If that leaves us dead, no activation.
 */
public final class RecurringLoss309 {
    private RecurringLoss309() { }

    /** life we lose at our next upkeep from our own permanents' upkeep triggers */
    public static int upkeepLoss(final Player ai) {
        int loss = 0;
        for (final Card c : ai.getCardsIn(ZoneType.Battlefield)) {
            for (final Trigger t : c.getTriggers()) {
                if (t.getMode() != TriggerType.Phase || !"Upkeep".equals(t.getParam("Phase"))
                        || !"You".equals(t.getParam("ValidPlayer"))) {
                    continue;
                }
                final SpellAbility sa = t.ensureAbility();
                if (sa == null || sa.getApi() != ApiType.LoseLife || sa.hasParam("UnlessCost")
                        || t.hasParam("OptionalDecider")) {
                    continue;
                }
                final String def = sa.getParamOrDefault("Defined", "You");
                if (!"You".equals(def)) {
                    continue;
                }
                sa.setActivatingPlayer(ai);
                loss += Math.max(0, AbilityUtils.calculateAmount(c, sa.getParam("LifeAmount"), sa));
            }
        }
        return loss;
    }

    /** combat damage the opponents' creatures can deal us before our next upkeep */
    public static int threatBeforeUpkeep(final Player ai) {
        final PhaseHandler ph = ai.getGame().getPhaseHandler();
        if (!ph.isPlayerTurn(ai) && ph.getPhase().isAfter(PhaseType.COMBAT_DECLARE_ATTACKERS)) {
            return 0; // the opponent's combat is over, our upkeep comes next
        }
        final List<Integer> hits = new ArrayList<>();
        for (final Player opp : ai.getOpponents()) {
            for (final Card c : opp.getCreaturesInPlay()) {
                if (ComputerUtilCombat.canAttackNextTurn(c, ai)) {
                    hits.add(ComputerUtilCombat.damageIfUnblocked(c, ai, null, false));
                }
            }
        }
        int blockers = 0;
        for (final Card c : ai.getCreaturesInPlay()) {
            if (c.isUntapped() || ph.isPlayerTurn(ai) && c.hasKeyword(forge.game.keyword.Keyword.VIGILANCE)) {
                blockers++;
            }
        }
        hits.sort((a, b) -> b - a);
        int sum = 0;
        for (int i = blockers; i < hits.size(); i++) {
            sum += hits.get(i);
        }
        return sum;
    }

    /** adding {@code extra} to our recurring loss would leave us dead by our next upkeep */
    public static boolean deadly(final Player ai, final int extra) {
        if (!NewAi.on(ai, 309)) {
            return false;
        }
        return ai.getLife() - upkeepLoss(ai) - extra - threatBeforeUpkeep(ai) <= 0;
    }
}
