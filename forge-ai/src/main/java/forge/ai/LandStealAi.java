package forge.ai;

import forge.game.ability.AbilityUtils;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.spellability.TargetRestrictions;
import forge.game.zone.ZoneType;

/**
 * magic-tools: "gain control of all <AllValid> target player controls" (Gilt-Leaf Archdruid, ElfBall's finisher).
 * ControlGainAi only looked for target cards: with ValidTgts$ Player no card can be targeted, so the ability was
 * never activated. Targets the opponent who loses the most permanents.
 */
public final class LandStealAi {
    private LandStealAi() { }

    /** null when the ability is not a "target player, take all" one. */
    public static AiAbilityDecision choosePlayer(final Player ai, final SpellAbility sa) {
        final TargetRestrictions tgt = sa.getTargetRestrictions();
        if (tgt == null || !sa.hasParam("AllValid") || !tgt.canTgtPlayer() || tgt.canTgtPermanent()) {
            return null;
        }
        Player best = null;
        int bestCount = 0;
        for (final Player opp : ai.getOpponents()) {
            if (!sa.canTarget(opp)) {
                continue;
            }
            sa.resetTargets();
            sa.getTargets().add(opp);
            final int count = AbilityUtils.filterListByType(opp.getCardsIn(ZoneType.Battlefield), sa.getParam("AllValid"), sa).size();
            if (count > bestCount) {
                best = opp;
                bestCount = count;
            }
        }
        sa.resetTargets();
        if (best == null) {
            return new AiAbilityDecision(0, AiPlayDecision.TargetingFailed);
        }
        sa.getTargets().add(best);
        return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
    }
}
