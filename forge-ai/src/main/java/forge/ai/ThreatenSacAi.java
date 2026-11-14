package forge.ai;

import java.util.EnumSet;

import forge.game.Game;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardLists;
import forge.game.cost.Cost;
import forge.game.cost.CostPart;
import forge.game.cost.CostSacrifice;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;
import forge.game.spellability.SpellAbility;

// magic-tools: "gain control until end of turn" + sacrifice outlet (Claim the Firstborn + Phyrexian Ghoul).
// ControlGainEffect gives the stolen creature SacMe=6; once combat is over it only goes back to its owner,
// so a sacrifice outlet should eat it rather than let it go.
public final class ThreatenSacAi {
    private ThreatenSacAi() {
    }

    // outlets whose effect is good for the AI whatever the moment (Witch's Oven, Tymaret, Carrion Feeder...)
    private static final EnumSet<ApiType> HARMLESS = EnumSet.of(ApiType.Token, ApiType.DealDamage, ApiType.Pump,
            ApiType.PutCounter, ApiType.Draw, ApiType.GainLife, ApiType.LoseLife, ApiType.Scry, ApiType.Surveil);

    public static boolean hasStolenFodder(final Player ai, final Card outlet) {
        final Game game = ai.getGame();
        if (!game.getPhaseHandler().getPhase().isAfter(PhaseType.COMBAT_END)) {
            return false;
        }
        return ai.getCreaturesInPlay().anyMatch(c -> isStolenUntilEot(ai, c) && !c.equals(outlet));
    }

    public static boolean isStolenUntilEot(final Player ai, final Card c) {
        return c.getController() == ai && !ai.equals(c.getOwner()) && c.hasSVar("SacMe");
    }

    // an activated ability whose sacrifice cost can be paid with a stolen creature, after combat
    public static boolean eatsStolenCreature(final Player ai, final SpellAbility sa) {
        if (!sa.isActivatedAbility() || sa.getApi() == null || !HARMLESS.contains(sa.getApi())) {
            return false;
        }
        final Card source = sa.getHostCard();
        if (!hasStolenFodder(ai, source)) {
            return false;
        }
        final Cost cost = sa.getPayCosts();
        if (cost == null) {
            return false;
        }
        final CardCollection stolen = CardLists.filter(ai.getCreaturesInPlay(), c -> isStolenUntilEot(ai, c) && !c.equals(source));
        for (final CostPart part : cost.getCostParts()) {
            if (part instanceof CostSacrifice sac && !sac.payCostFromSource()) {
                return !CardLists.getValidCards(stolen, sac.getType().split(";"), ai, source, sa).isEmpty();
            }
        }
        return false;
    }

    // a permanent of the AI with an activated "sacrifice a creature" ability (Ghoul, Oven, Tymaret, Altar...)
    public static boolean hasSacOutlet(final Player ai) {
        for (final Card c : ai.getCardsIn(ZoneType.Battlefield)) {
            for (final SpellAbility sa : c.getSpellAbilities()) {
                if (!sa.isActivatedAbility() || sa.getPayCosts() == null) {
                    continue;
                }
                for (final CostPart part : sa.getPayCosts().getCostParts()) {
                    if (part instanceof CostSacrifice sac && !sac.payCostFromSource() && sac.getType().contains("Creature")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // without an outlet, a creature stolen until end of turn only hits once: steal a big one (power 3+)
    // or the opponent's only untapped blocker, not a 1/1 on turn 2 (Tymaret, 24/10)
    public static boolean worthStealingWithoutOutlet(final Player ai, final Card c) {
        if (hasSacOutlet(ai) || c.getNetCombatDamage() >= 3) {
            return true;
        }
        final Player opp = c.getController();
        return opp != null && opp.getCreaturesInPlay().filter(b -> b.isUntapped() && !b.equals(c)).isEmpty()
                && !ai.getCreaturesInPlay().isEmpty();
    }
}
