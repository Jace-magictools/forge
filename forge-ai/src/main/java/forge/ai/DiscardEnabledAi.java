package forge.ai;

import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.player.Player;
import forge.game.staticability.StaticAbility;
import forge.game.staticability.StaticAbilityMode;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 136 (tournament v21, Schizoph). With Fiery Temper and Violent Eruption in hand, the AI discarded
 * Asmoranomardicadaistinaculdacar to Faithless Looting (9 times in 50 games): the "worst card" fallback picks the
 * only creature, and its printed cost is 0. That card is exactly the one that becomes castable for {B/R} once we
 * have discarded this turn.
 * General rule: when the AI picks its own discards, a card in our hand whose own cost becomes an alternative cost or
 * gets reduced because we discarded this turn (Asmoranomardicadaistinaculdacar, Hollow One...) is kept as long as
 * enough other cards can be discarded.
 */
public final class DiscardEnabledAi {
    private DiscardEnabledAi() { }

    public static boolean isEnabledByDiscard(final Card c) {
        for (final StaticAbility st : c.getStaticAbilities()) {
            final String svar;
            if (st.checkMode(StaticAbilityMode.AlternativeCost)) {
                svar = st.getParam("CheckSVar");
            } else if (st.checkMode(StaticAbilityMode.ReduceCost)) {
                svar = st.getParam("Amount");
            } else {
                continue;
            }
            final String valid = st.hasParam("ValidSA") ? st.getParam("ValidSA") : st.getParam("ValidCard");
            if (svar == null || valid == null || !valid.contains("Self")) {
                continue;
            }
            if (c.hasSVar(svar) && c.getSVar(svar).contains("CardsDiscardedThisTurn")) {
                return true;
            }
        }
        return false;
    }

    /** Cards to set aside before choosing the rest of the discards (empty if too few others remain). */
    public static CardCollection keep(final Player ai, final CardCollection validCards, final int discardsLeft) {
        final CardCollection kept = new CardCollection();
        for (final Card c : validCards) {
            if (ai.equals(c.getOwner()) && c.isInZone(ZoneType.Hand) && isEnabledByDiscard(c)) {
                kept.add(c);
            }
        }
        if (kept.isEmpty() || validCards.size() - kept.size() < discardsLeft) {
            return new CardCollection();
        }
        return kept;
    }
}
