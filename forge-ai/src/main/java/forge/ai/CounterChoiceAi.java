package forge.ai;

import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;

/**
 * magic-tools: the ALWAYS_COUNTER_* profile flags hold whatever the mana value of the target.
 * CounterAi only read them below MIN_SPELL_CMC_TO_COUNTER (0 in Default.ai, so never), and the
 * 30 % / 75 % chances for 1 and 2 mana spells let Ethereal Armor or Unable to Scream resolve
 * while merfolk held Counterspell with two Islands open. Also fixes the && / || precedence that
 * made damage, removal and life loss spells count regardless of their flag.
 */
public final class CounterChoiceAi {
    private CounterChoiceAi() { }

    public static boolean alwaysCounter(final Player ai, final SpellAbility tgtSA) {
        if (tgtSA == null) {
            return false;
        }
        final ApiType api = tgtSA.getApi();
        final Card host = tgtSA.getHostCard();
        if (api == ApiType.Counter) {
            return AiProfileUtil.getBoolProperty(ai, AiProps.ALWAYS_COUNTER_OTHER_COUNTERSPELLS);
        }
        if (api == ApiType.DealDamage || api == ApiType.LoseLife || api == ApiType.DamageAll) {
            return AiProfileUtil.getBoolProperty(ai, AiProps.ALWAYS_COUNTER_DAMAGE_SPELLS);
        }
        if (api == ApiType.Destroy || api == ApiType.DestroyAll || api == ApiType.Sacrifice || api == ApiType.SacrificeAll) {
            return AiProfileUtil.getBoolProperty(ai, AiProps.ALWAYS_COUNTER_REMOVAL_SPELLS);
        }
        if (api == ApiType.Pump || api == ApiType.PumpAll) {
            return AiProfileUtil.getBoolProperty(ai, AiProps.ALWAYS_COUNTER_PUMP_SPELLS);
        }
        if (api == ApiType.Attach || (tgtSA.isSpell() && host != null && host.isAura())) {
            return AiProfileUtil.getBoolProperty(ai, AiProps.ALWAYS_COUNTER_AURAS);
        }
        return false;
    }
}
