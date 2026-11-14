package forge.ai.ability;

import java.util.List;

import forge.ai.AiPlayDecision;
import forge.ai.ComputerUtilAbility;
import forge.ai.ComputerUtilMana;
import forge.ai.PlayerControllerAi;
import forge.card.mana.ManaCost;
import forge.game.ability.AbilityUtils;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.phase.PhaseHandler;
import forge.game.player.Player;
import forge.game.spellability.AbilitySub;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 120. A triggered "choose one" whose mode adds mana (Culber and Stamets:
 * "+1/+1 counter, or add two mana of any one color") was never given the mana mode, because
 * ManaAi refuses mana outside MAIN2. On our own main phase, take the mana mode when that mana
 * lets us cast now a spell from hand that the untapped sources alone can't pay for.
 */
public final class ManaCharm120 {
    private ManaCharm120() { }

    // canPlaySa of a spell in hand checks its own ETB charm, which would come back here
    private static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> Boolean.FALSE);

    public static AbilitySub pick(Player ai, SpellAbility sa, List<AbilitySub> choices, int num) {
        if (num != 1 || !sa.isTrigger() || ACTIVE.get()
                || !sa.getHostCard().isInZone(ZoneType.Battlefield)) {
            return null;
        }
        PhaseHandler ph = ai.getGame().getPhaseHandler();
        if (!ph.isPlayerTurn(ai) || !ph.getPhase().isMain()) {
            return null;
        }
        for (AbilitySub sub : choices) {
            if (sub.getApi() != ApiType.Mana || sub.hasParam("AILogic") || sub.usesTargeting()
                    || sub.getSubAbility() != null || !sub.hasParam("Produced")) {
                continue;
            }
            sub.setActivatingPlayer(ai);
            int amount = AbilityUtils.calculateAmount(sub.getHostCard(), sub.getParamOrDefault("Amount", "1"), sub)
                    * sub.getParam("Produced").split(" ").length;
            if (amount <= 0) {
                continue;
            }
            ACTIVE.set(Boolean.TRUE);
            try {
                if (enablesSpell(ai, sub, amount)) {
                    return sub;
                }
            } finally {
                ACTIVE.set(Boolean.FALSE);
            }
        }
        return null;
    }

    private static boolean enablesSpell(Player ai, SpellAbility sub, int amount) {
        final int avail = ComputerUtilMana.getAvailableManaEstimate(ai, true);
        final Card host = sub.getHostCard();
        List<SpellAbility> all = ComputerUtilAbility.getSpellAbilities(ai.getCardsIn(ZoneType.Hand), ai);
        for (SpellAbility testSa : ComputerUtilAbility.getOriginalAndAltCostAbilities(all, ai)) {
            // instants are often held for the opponent's turn (same caution as ManaAi rituals)
            if (!testSa.isSpell() || testSa.getHostCard() == host || testSa.getPayCosts() == null
                    || testSa.getHostCard().isInstant()) {
                continue;
            }
            ManaCost cost = testSa.getPayCosts().getTotalMana();
            int cmc = cost.getCMC();
            if (cost.countX() > 0 || cmc <= avail || cmc > avail + amount) {
                continue;
            }
            SpellAbility noCost = testSa.copyWithNoManaCost();
            if (noCost == null) {
                continue;
            }
            noCost.setActivatingPlayer(ai);
            if (((PlayerControllerAi) ai.getController()).getAi().canPlaySa(noCost) == AiPlayDecision.WillPlay) {
                return true;
            }
        }
        return false;
    }
}
