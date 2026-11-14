package forge.ai;

import forge.game.card.Card;
import forge.game.card.CardLists;
import forge.game.card.CardPredicates;
import forge.game.phase.PhaseHandler;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/**
 * magic-tools: stock AI keeps creatures for main 2, then animates a creature land at the beginning of
 * combat to attack (Mishra, 27/10: Lavaclaw Reaches for {1}{B}{R} and 2 damage with 4 lands) and the
 * Foundry Inspector waiting in hand is no longer affordable in main 2. A land does not animate itself to
 * attack when a permanent spell in hand is castable now but would not be once the animation is paid and
 * the land is tapped to attack.
 */
public final class ManlandMain2Ai {
    private ManlandMain2Ai() { }

    public static boolean keepsManaForHand(final Player ai, final SpellAbility sa, final Card land) {
        final PhaseHandler ph = ai.getGame().getPhaseHandler();
        if (land == null || !land.isLand() || land.isCreature() || land != sa.getHostCard()
                || !ph.isPlayerTurn(ai) || !ph.getPhase().isBefore(PhaseType.MAIN2)) {
            return false;
        }
        int spent = sa.getPayCosts() == null || sa.getPayCosts().getTotalMana() == null
                ? 0 : sa.getPayCosts().getTotalMana().getCMC();
        if (!land.isTapped() && !land.getManaAbilities().isEmpty()) {
            spent++; // the land taps to attack and no longer makes mana
        }
        if (spent == 0) {
            return false;
        }
        // colours ignored: canPayManaCost does not see the hand as castable at the beginning of combat
        final int available = untappedMana(ai);
        for (Card c : CardLists.filter(ai.getCardsIn(ZoneType.Hand), CardPredicates.NON_LANDS)) {
            final int cmc = c.getCMC();
            if (c.isPermanent() && cmc > 0 && cmc <= available && cmc > available - spent && !refusedForEtb(ai, c)) {
                return true;
            }
        }
        return false;
    }

    /**
     * magic-tools: rule 245, no mana is kept for a permanent the AI will refuse in main 2 for its enter trigger
     * (Skinrender with no target but itself, Demogorgon T10 to T16: the land stayed home turn after turn).
     */
    private static boolean refusedForEtb(final Player ai, final Card c) {
        if (!(ai.getController() instanceof PlayerControllerAi pc) || c.getFirstSpellAbility() == null) {
            return false;
        }
        final SpellAbility spell = c.getFirstSpellAbility();
        final Player before = spell.getActivatingPlayer();
        spell.setActivatingPlayer(ai);
        final boolean ok = pc.getAi().checkETBEffects(c, spell, null);
        spell.setActivatingPlayer(before);
        return !ok;
    }

    /** One mana per untapped source (getAvailableManaEstimate counts "Combo B R" as two), net of filter costs. */
    static int untappedMana(final Player ai) {
        int total = 0;
        for (final Card src : ai.getCardsIn(ZoneType.Battlefield)) {
            if (src.isTapped() || (src.isCreature() && src.isSick())) {
                continue;
            }
            int best = 0;
            for (final SpellAbility ma : src.getManaAbilities()) {
                final String produced = ma.getParamOrDefault("Produced", "");
                final int kinds = produced.startsWith("Combo") || produced.startsWith("Any") ? 1 : produced.split(" ").length;
                int amount;
                try {
                    amount = Integer.parseInt(ma.getParamOrDefault("Amount", "1"));
                } catch (final NumberFormatException e) {
                    amount = 1;
                }
                final int cost = ma.getPayCosts().getTotalMana() == null ? 0 : ma.getPayCosts().getTotalMana().getCMC();
                best = Math.max(best, kinds * amount - cost);
            }
            total += best;
        }
        return total;
    }
}
