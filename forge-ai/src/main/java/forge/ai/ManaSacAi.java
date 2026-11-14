package forge.ai;

import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardCollectionView;
import forge.game.cost.CostPart;
import forge.game.cost.CostSacrifice;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;

/**
 * magic-tools rule 59: a real creature is not sacrificed for mana (Phyrexian Altar, Ashnod's Altar) to pay for a spell
 * that is not clearly bigger. Forge paid with its worst creature whenever nothing was marked to sacrifice: Smeagol
 * cast Sméagol, Helpful Guide (4/2) then fed it to Phyrexian Altar for the last mana of Insidious Roots (v8 tournament,
 * 11 times). Fodder (tokens, cards that come back, SacMe, threatened creatures), loops and non-creatures still go;
 * a real creature only pays for a spell of mana value at least its own + 2 (Iron Myr into Wurmcoil Engine).
 */
public final class ManaSacAi {
    private ManaSacAi() { }

    public static final int UPGRADE = 2;

    public static boolean worthPaying(final Player ai, final SpellAbility ma, final SpellAbility sa) {
        if (ma.getPayCosts() == null || !ai.getController().isAI()) {
            return true;
        }
        for (final CostPart part : ma.getPayCosts().getCostParts()) {
            if (!(part instanceof CostSacrifice) || part.payCostFromSource() || "OriginalHost".equals(part.getType())
                    || "All".equals(part.getAmount())) {
                continue;
            }
            if (PersistLoopAi.anyLoopCard(ai, ma) != null) {
                return true;
            }
            final CardCollection exclude = new CardCollection();
            if (AiCardMemory.getMemorySet(ai, AiCardMemory.MemorySet.PAYS_SAC_COST) != null) {
                exclude.addAll(AiCardMemory.getMemorySet(ai, AiCardMemory.MemorySet.PAYS_SAC_COST));
            }
            final AiController aic = ((PlayerControllerAi) ai.getController()).getAi();
            final CardCollectionView choices = aic.chooseSacrificeType(part.getType(), ma, ma.isTrigger(),
                    part.getAbilityAmount(ma), exclude);
            if (choices == null) {
                return true; // the cost check that follows refuses it anyway
            }
            for (final Card c : choices) {
                if (keeps(ai, c, sa)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** the creature is worth more than what its mana would buy */
    static boolean keeps(final Player ai, final Card c, final SpellAbility sa) {
        if (!c.isCreature() || c.isToken() || FodderAi.isFree(ai, c) || FodderAi.isCheap(ai, c)
                || c.hasSVar("SacMe") || ComputerUtil.shouldSacrificeThreatenedCard(ai, c, sa)) {
            return false;
        }
        if (sa != null && sa.isSpell() && sa.getHostCard() != null
                && sa.getHostCard().getCMC() >= c.getCMC() + UPGRADE) {
            return false;
        }
        return true;
    }
}
