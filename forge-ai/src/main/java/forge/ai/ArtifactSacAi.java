package forge.ai;

import forge.game.Game;
import forge.game.ability.AbilityUtils;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardLists;
import forge.game.card.CardPredicates;
import forge.game.combat.Combat;
import forge.game.cost.CostSacrifice;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;

/**
 * magic-tools: Kavaron (Atog, Arcbound Ravager, Weapons Manufacturing). Forge sacrificed the Ravager to its
 * own ability, and fed every artifact (lands included) to Atog for a few extra points. A "Sacrifice an
 * artifact: this gets bigger" outlet never pays with itself, and pays with a nontoken artifact only when the
 * damage this turn is lethal: unblocked attackers, the outlet's growth, and the Munitions-like tokens that
 * deal damage when they leave the battlefield.
 */
public final class ArtifactSacAi {
    private ArtifactSacAi() { }

    /** Sac<1/Artifact...> to pump or put counters on the host itself. */
    public static boolean isSelfGrowthOutlet(final SpellAbility sa) {
        if (sa == null || sa.getPayCosts() == null || sa.usesTargeting()) {
            return false;
        }
        final CostSacrifice sac = sa.getPayCosts().getCostPartByType(CostSacrifice.class);
        if (sac == null || sac.payCostFromSource() || !sac.getType().startsWith("Artifact")) {
            return false;
        }
        final String defined = sa.getParamOrDefault("Defined", "Self");
        return (sa.getApi() == ApiType.Pump || sa.getApi() == ApiType.PutCounter) && defined.equals("Self");
    }

    /** The outlet does not eat itself. */
    public static CardCollection withoutHost(final CardCollection list, final SpellAbility sa) {
        if (isSelfGrowthOutlet(sa) && list.contains(sa.getHostCard())) {
            final CardCollection rest = new CardCollection(list);
            rest.remove(sa.getHostCard());
            return rest;
        }
        return list;
    }

    /** Damage dealt by a card's own "when this leaves the battlefield, it deals N damage" trigger. */
    public static int leaveDamage(final Card c) {
        int dmg = 0;
        for (final Trigger t : c.getTriggers()) {
            if (t.getMode() != TriggerType.ChangesZone || !t.getParamOrDefault("Origin", "").contains("Battlefield")
                    || !"Card.Self".equals(t.getParam("ValidCard"))) {
                continue;
            }
            final SpellAbility exec = t.ensureAbility();
            if (exec != null && exec.getApi() == ApiType.DealDamage && exec.hasParam("NumDmg")) {
                dmg += AbilityUtils.calculateAmount(c, exec.getParam("NumDmg"), exec);
            }
        }
        return dmg;
    }

    private static int growth(final SpellAbility sa) {
        if (sa.getApi() == ApiType.Pump) {
            return AbilityUtils.calculateAmount(sa.getHostCard(), sa.getParamOrDefault("NumAtt", "0"), sa);
        }
        return AbilityUtils.calculateAmount(sa.getHostCard(), sa.getParamOrDefault("CounterNum", "1"), sa);
    }

    /**
     * true: pay now (lethal); false: keep the artifacts; null: let Forge decide (responses, blocks, tokens to spare).
     */
    public static Boolean decide(final Player ai, final SpellAbility sa) {
        if (!isSelfGrowthOutlet(sa)) {
            return null;
        }
        final Game game = ai.getGame();
        final Card host = sa.getHostCard();
        if (!game.getStack().isEmpty()) {
            return null;
        }
        final CardCollection fodder = withoutHost(CardLists.filter(
                CardLists.getValidCards(ai.getCardsIn(ZoneType.Battlefield),
                        sa.getPayCosts().getCostPartByType(CostSacrifice.class).getType().split(";"), ai, host, sa),
                CardPredicates.canBeSacrificedBy(sa, false)), sa);
        if (fodder.isEmpty()) {
            return null;
        }
        int leave = 0;
        for (final Card c : fodder) {
            leave += leaveDamage(c);
        }
        final Combat combat = game.getCombat();
        final boolean afterBlocks = combat != null && game.getPhaseHandler().inCombat()
                && game.getPhaseHandler().getPhase().isAfter(PhaseType.COMBAT_DECLARE_ATTACKERS)
                && game.getPhaseHandler().getPhase().isBefore(PhaseType.COMBAT_END);
        final Player opp = afterBlocks && combat.isAttacking(host)
                ? combat.getDefendingPlayerRelatedTo(host) : ai.getWeakestOpponent();
        if (opp == null) {
            return null;
        }
        int damage = leave;
        if (afterBlocks && game.getPhaseHandler().isPlayerTurn(ai)
                && !game.getPhaseHandler().getPhase().isAfter(PhaseType.COMBAT_FIRST_STRIKE_DAMAGE)) {
            for (final Card a : combat.getAttackers()) {
                if (!combat.isBlocked(a) && opp.equals(combat.getDefendingPlayerRelatedTo(a))) {
                    damage += a.getNetCombatDamage();
                    if (a.equals(host)) {
                        damage += growth(sa) * fodder.size();
                    }
                }
            }
        }
        if (damage >= opp.getLife() && opp.canLoseLife()) {
            return true;
        }
        if (afterBlocks && (combat.isBlocked(host) || combat.isBlocking(host))) {
            return null; // save the outlet or win the fight: Forge's combat logic
        }
        if (fodder.anyMatch(Card::isToken)) {
            return null;
        }
        return false;
    }
}
