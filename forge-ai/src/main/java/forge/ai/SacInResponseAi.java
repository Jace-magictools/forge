package forge.ai;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import forge.game.Game;
import forge.game.GameObject;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardLists;
import forge.game.cost.CostPart;
import forge.game.cost.CostSacrifice;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/**
 * magic-tools rule 321 (a tester, 17/11; measure on 1 200 arbiter logs: 18 positions where the AI passed while an
 * opponent's spell or ability on top of the stack took one of its creatures and a sacrifice outlet could have used
 * it first). Kitchen Finks under Swords to Plowshares with Viscera Seer: sacrifice it in response, persist brings it
 * back and the scry comes for free. General rule: a creature that the top of the stack removes anyway is fed to an
 * activated ability whose cost sacrifices a creature (any effect, not a mana ability), and the cost takes that one.
 */
public final class SacInResponseAi {
    private SacInResponseAi() { }

    /** The ability chosen in response, and the creature its sacrifice cost must take. */
    private static final Map<SpellAbility, Card> CHOSEN = new WeakHashMap<>();

    public static Card chosenFor(final SpellAbility sa) {
        return sa == null ? null : CHOSEN.get(sa);
    }

    public static void clear(final SpellAbility sa) {
        if (sa != null) {
            CHOSEN.remove(sa);
        }
    }

    private static CostSacrifice sacPart(final SpellAbility sa) {
        if (sa.getPayCosts() == null) {
            return null;
        }
        for (final CostPart part : sa.getPayCosts().getCostParts()) {
            if (part instanceof CostSacrifice) {
                return (CostSacrifice) part;
            }
        }
        return null;
    }

    public static SpellAbility response(final Player ai) {
        if (!NewAi.on(ai, 321)) {
            return null;
        }
        final Game game = ai.getGame();
        if (game.getStack().isEmpty()) {
            return null;
        }
        final SpellAbility top = game.getStack().peekAbility();
        if (top == null || top.getActivatingPlayer() == null || !top.getActivatingPlayer().isOpponentOf(ai)) {
            return null;
        }
        final CardCollection doomed = new CardCollection();
        final List<GameObject> threatened = ComputerUtil.predictThreatenedObjects(ai, null, true);
        for (final GameObject o : threatened) {
            if (o instanceof Card c && c.isCreature() && c.getController().equals(ai) && c.isInPlay()) {
                doomed.add(c);
            }
        }
        if (doomed.isEmpty()) {
            return null;
        }
        // the creature whose death is worth the most first: persist / undying / "when this dies" returns or pays
        ComputerUtilCard.sortByEvaluateCreature(doomed);
        final CardCollection returning = CardLists.filter(doomed, ComputerUtilCard::hasActiveUndyingOrPersist);
        doomed.removeAll(returning);
        doomed.addAll(0, returning);
        for (final SpellAbility sa : ComputerUtilAbility.getSpellAbilities(ai.getCardsIn(ZoneType.Battlefield), ai)) {
            if (sa.isSpell() || sa.isManaAbility() || sa.isTrigger()) {
                continue;
            }
            final CostSacrifice sac = sacPart(sa);
            if (sac == null || "All".equalsIgnoreCase(sac.getAmount()) || sac.getType().equals("OriginalHost")) {
                continue;
            }
            sa.setActivatingPlayer(ai);
            if (!sa.canPlay() || !ComputerUtilCost.canPayCost(sa, ai, false)) {
                continue;
            }
            Card pick = null;
            if (sac.payCostFromSource()) {
                if (doomed.contains(sa.getHostCard())) {
                    pick = sa.getHostCard();
                }
            } else if (sac.getAbilityAmount(sa) == 1) {
                final CardCollection valid = CardLists.getValidCards(doomed, sac.getType().split(";"), ai, sa.getHostCard(), sa);
                if (!valid.isEmpty()) {
                    pick = valid.getFirst();
                }
            }
            if (pick == null) {
                continue;
            }
            if (sa.usesTargeting()) {
                final AiController aic = ((PlayerControllerAi) ai.getController()).getAi();
                CHOSEN.put(sa, pick);
                if (aic.canPlaySa(sa) != AiPlayDecision.WillPlay) {
                    CHOSEN.remove(sa);
                    sa.resetTargets();
                    continue;
                }
            }
            CHOSEN.put(sa, pick);
            return sa;
        }
        return null;
    }
}
