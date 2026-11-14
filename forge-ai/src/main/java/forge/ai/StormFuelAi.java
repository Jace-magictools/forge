package forge.ai;

import forge.card.mana.ManaCost;
import forge.game.ability.AbilityUtils;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.keyword.Keyword;
import forge.game.phase.PhaseHandler;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 109, a free spell is Storm fuel (tester 30/10 12:31, Gobelin - Patrilock: Gut Shot fired at the
 * opponent's face for 1 at the end of their turn while Empty the Warrens waited in hand; cast before it, Gut Shot is
 * one more copy, two more goblins).
 * <p>
 * An instant or sorcery of the hand that costs no mana (zero, or Phyrexian shards only) and does not target a permanent waits
 * while a Storm card is in hand, unless it is our main phase with that Storm card castable now, or it is lethal.
 */
public final class StormFuelAi {
    private StormFuelAi() { }

    public static boolean holdForStorm(final Player ai, final SpellAbility sa) {
        if (!sa.isSpell()) {
            return false;
        }
        final Card host = sa.getHostCard();
        if (!ai.getCardsIn(ZoneType.Hand).contains(host) || host.hasKeyword(Keyword.STORM)
                || !(host.isInstant() || host.isSorcery()) || !isFree(host.getManaCost())) {
            return false;
        }
        Card storm = null;
        for (final Card c : ai.getCardsIn(ZoneType.Hand)) {
            if (c != host && c.hasKeyword(Keyword.STORM)) {
                storm = c;
                break;
            }
        }
        if (storm == null) {
            return false;
        }
        for (SpellAbility s = sa; s != null; s = s.getSubAbility()) {
            if (s.usesTargeting() && !s.getTargets().getTargetCards().isEmpty()) {
                return false;
            }
            if (s.getApi() == ApiType.DealDamage && s.hasParam("NumDmg")) {
                final int dmg = AbilityUtils.calculateAmount(host, s.getParam("NumDmg"), s);
                for (final Player p : s.getTargets().getTargetPlayers()) {
                    if (p.isOpponentOf(ai) && p.getLife() <= dmg) {
                        return false;
                    }
                }
            }
        }
        final PhaseHandler ph = ai.getGame().getPhaseHandler();
        if (ph.isPlayerTurn(ai) && ph.getPhase().isMain()
                && ComputerUtilMana.getAvailableManaEstimate(ai, false) + ai.getManaPool().totalMana()
                        >= storm.getManaCost().getCMC()) {
            return false;
        }
        return true;
    }

    static boolean isFree(final ManaCost cost) {
        return cost.getGenericCost() == 0 && cost.getCMC() == cost.getPhyrexianCount();
    }
}
