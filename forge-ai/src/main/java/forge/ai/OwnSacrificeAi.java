package forge.ai;

import java.util.EnumSet;

import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardLists;
import forge.game.cost.Cost;
import forge.game.cost.CostPart;
import forge.game.cost.CostSacrifice;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.spellability.SpellAbilityStackInstance;
import forge.game.trigger.WrappedAbility;

/**
 * magic-tools: rule 116 (follow-up of rule 114, a tester report 30/10 19:03). The AI did not see its own "sacrifice
 * this" effects as a threat: an unpaid echo, an evoke sacrifice, "at the beginning of the end step, sacrifice it"
 * (Ball Lightning, Sneak Attack). With a free sacrifice outlet on the battlefield (Goblin Bombardment, Viscera Seer,
 * Altar of Dementia), the creature was lost for nothing instead of feeding the outlet first.
 * General rule: a mandatory "sacrifice this" of ours on the stack threatens its host, like a destroy spell would.
 * The usual threatened-card logic then sacrifices it first to an outlet (SacCost priority, ManaSacAi), uses it before
 * it goes, and does not spend pump or counters on it.
 */
public final class OwnSacrificeAi {
    private OwnSacrificeAi() { }

    public static boolean threatensHost(final Player ai, final ApiType saviourApi, final SpellAbility sa) {
        if (sa == null || sa.getApi() != ApiType.Sacrifice || sa.hasParam("Optional") || sa.usesTargeting()) {
            return false;
        }
        if (!"Self".equals(sa.getParamOrDefault("SacValid", "Self")) || sa.hasParam("UnlessCost")
                || sa.hasParam("CumulativeUpkeep")) {
            return false;
        }
        // a pump, counters, protection or regeneration does not stop a sacrifice
        if (saviourApi == ApiType.Pump || saviourApi == ApiType.PumpAll || saviourApi == ApiType.PutCounter
                || saviourApi == ApiType.PutCounterAll || saviourApi == ApiType.Protection
                || saviourApi == ApiType.Regenerate) {
            return false;
        }
        final Card host = sa.getHostCard();
        if (host == null || !host.isInPlay() || !ai.equals(host.getController()) || !ai.equals(sa.getActivatingPlayer())) {
            return false;
        }
        if (sa.hasParam("Echo")) {
            final Cost echo = new Cost(sa.getParam("Echo"), true);
            if (echo.getTotalMana() == null) {
                return false;
            }
            final boolean canPay = ComputerUtilMana.getAvailableManaEstimate(ai, false) >= echo.getTotalMana().getCMC();
            return !canPay || !EchoPayAi.shouldPay(ai, sa, echo);
        }
        return true;
    }

    // outlets whose effect is good for us whatever the moment (as ThreatenSacAi, plus Altar of Dementia's mill)
    private static final EnumSet<ApiType> HARMLESS = EnumSet.of(ApiType.Token, ApiType.DealDamage, ApiType.Pump,
            ApiType.PutCounter, ApiType.Draw, ApiType.GainLife, ApiType.LoseLife, ApiType.Scry, ApiType.Surveil,
            ApiType.Mill, ApiType.Mana);

    /** c is about to be sacrificed by one of our own effects on the stack. */
    public static boolean isDoomed(final Player ai, final Card c) {
        if (c == null || ai.getGame().getStack().isEmpty()) {
            return false;
        }
        for (final SpellAbilityStackInstance si : ai.getGame().getStack()) {
            SpellAbility s = si.getSpellAbility();
            if (s.isWrapper()) {
                s = ((WrappedAbility) s).getWrappedAbility();
            }
            for (SpellAbility cur = s; cur != null; cur = cur.getSubAbility()) {
                if (c.equals(cur.getHostCard()) && threatensHost(ai, null, cur)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** an activated "sacrifice a creature" ability with a harmless effect can eat a creature we are about to lose. */
    public static boolean eatsDoomedCreature(final Player ai, final SpellAbility sa) {
        if (!sa.isActivatedAbility() || sa.getApi() == null || !HARMLESS.contains(sa.getApi()) || sa.getPayCosts() == null) {
            return false;
        }
        if (ai.getGame().getStack().isEmpty()) {
            return false;
        }
        final Card source = sa.getHostCard();
        final CardCollection doomed = CardLists.filter(ai.getCreaturesInPlay(), c -> !c.equals(source) && isDoomed(ai, c));
        if (doomed.isEmpty()) {
            return false;
        }
        for (final CostPart part : sa.getPayCosts().getCostParts()) {
            if (part instanceof CostSacrifice sac && !sac.payCostFromSource()) {
                return !CardLists.getValidCards(doomed, sac.getType().split(";"), ai, source, sa).isEmpty();
            }
        }
        return false;
    }
}
