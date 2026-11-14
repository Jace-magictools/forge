package forge.ai;

import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardCollectionView;
import forge.game.card.CardLists;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 177. A sacrifice that pays for removing an opponent's permanent (Dusk Rose Reliquary,
 * Bone Splinters, Eldrazi Devastator-like "sacrifice a creature: destroy / exile target creature") was only
 * paid with free or cheap fodder (FodderAi): Vampire held Dusk Rose Reliquary for 5 turns with five creatures
 * in play while Aurelia beat it down. Pay with our worst creature when the best permanent the removal can hit
 * is clearly worth more.
 */
public final class RemovalSacAi {
    private RemovalSacAi() { }

    /** a removal must take out at least this much more than the creature it costs (about a +1/+1) */
    static final int MARGIN = 40;

    private static boolean isRemoval(final SpellAbility x) {
        if (x == null || !x.usesTargeting()) {
            return false;
        }
        if (x.getApi() == ApiType.Destroy) {
            return true;
        }
        if (x.getApi() == ApiType.ChangeZone) {
            final String origin = x.getParamOrDefault("Origin", "");
            final String dest = x.getParamOrDefault("Destination", "");
            return origin.contains("Battlefield")
                    && (dest.equals("Exile") || dest.equals("Graveyard") || dest.equals("Library"));
        }
        return false;
    }

    private static int value(final Card c) {
        return c.isCreature() ? ComputerUtilCard.evaluateCreature(c)
                : ComputerUtilCard.evaluatePermanentList(new CardCollection(c)) * 30;
    }

    private static int bestTarget(final Player ai, final SpellAbility x) {
        int best = 0;
        for (SpellAbility s = x; s != null; s = s.getSubAbility()) {
            if (!isRemoval(s)) {
                continue;
            }
            final CardCollection opp = new CardCollection();
            for (final Player p : ai.getOpponents()) {
                opp.addAll(p.getCardsIn(ZoneType.Battlefield));
            }
            for (final Card c : CardLists.getTargetableCards(opp, s)) {
                best = Math.max(best, value(c));
            }
        }
        return best;
    }

    /** value of the best opponent's permanent the spell (or the permanent's enters trigger) removes, 0 if none */
    public static int removedValue(final Player ai, final SpellAbility sa) {
        if (sa == null) {
            return 0;
        }
        int best = bestTarget(ai, sa);
        final Card host = sa.getHostCard();
        if (sa.isSpell() && host != null && host.isPermanent()) {
            for (final Trigger t : host.getTriggers()) {
                if (t.getMode() == TriggerType.ChangesZone && "Battlefield".equals(t.getParam("Destination"))
                        && "Card.Self".equals(t.getParam("ValidCard"))) {
                    final SpellAbility exec = t.ensureAbility();
                    if (exec != null) {
                        exec.setActivatingPlayer(ai);
                        best = Math.max(best, bestTarget(ai, exec));
                    }
                }
            }
        }
        return best;
    }

    /** our worst creature, when what the removal takes out is clearly worth more; null otherwise */
    public static Card choose(final Player ai, final CardCollectionView typeList, final SpellAbility sa) {
        final Card host = sa == null ? null : sa.getHostCard();
        final CardCollection ours = CardLists.filter(typeList, c -> c.isCreature() && !c.equals(host));
        if (ours.isEmpty()) {
            return null;
        }
        final int removed = removedValue(ai, sa);
        if (removed == 0) {
            return null;
        }
        final Card worst = ComputerUtilCard.getWorstCreatureAI(ours);
        if (worst != null && ComputerUtilCard.evaluateCreature(worst) + MARGIN <= removed) {
            return worst;
        }
        return null;
    }
}
