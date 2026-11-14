package forge.ai;

import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.keyword.Keyword;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;

/**
 * magic-tools: rule 161 (banc « IA contre décisions humaines », a test game, 03/11). Adanto Vanguard (3/1 when
 * attacking, "Pay 4 life: gains indestructible") stayed home against a lone 1/1: combat prediction counted the
 * regeneration abilities a creature can pay for, not the "gains indestructible" ones, so a block looked deadly.
 * A creature that can give itself indestructible, at instant speed, with a cost its controller can pay now (and a life
 * cost that leaves a safe life total), counts as indestructible in combat prediction, like a regenerating one.
 */
public final class SelfIndestructibleAi {
    /** Life left after paying, same threshold as PumpAi's own life checks. */
    static final int LIFE_LEFT = 5;

    private SelfIndestructibleAi() { }

    public static boolean canSelfIndestructible(final Card card) {
        if (card == null || card.hasKeyword(Keyword.INDESTRUCTIBLE)) {
            return false;
        }
        final Player controller = card.getController();
        if (controller == null) {
            return false;
        }
        for (final SpellAbility sa : card.getSpellAbilities()) {
            if (!sa.isActivatedAbility() || sa.getApi() != ApiType.Pump || !sa.hasParam("KW")) {
                continue;
            }
            if (!sa.getParam("KW").contains("Indestructible") || sa.usesTargeting()
                    || sa.hasParam("SorcerySpeed") || sa.hasParam("ActivationPhases")) {
                continue;
            }
            final String defined = sa.getParamOrDefault("Defined", "Self");
            if (!"Self".equals(defined)) {
                continue;
            }
            sa.setActivatingPlayer(controller);
            if (!sa.canPlay() || !ComputerUtilCost.canPayCost(sa, controller, false)) {
                continue;
            }
            if (!ComputerUtilCost.checkLifeCost(controller, sa.getPayCosts(), card, LIFE_LEFT, sa)) {
                continue;
            }
            return true;
        }
        return false;
    }
}
