package forge.ai;

import java.util.Comparator;
import java.util.List;

import forge.game.ability.AbilityUtils;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CardLists;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 30, "each player loses, discards, sacrifices" spells (Smallpox, Death Cloud).
 * Forge judged each part on its own (the creature edict refused when nobody had a creature), so Pox
 * never cast Smallpox (pox32: refused 57 turns in 8 games). The whole chain is judged instead: what
 * each player loses (life, cards, its worst creatures, lands), cast when the opponent loses clearly more.
 */
public final class SymmetricChainAi {
    private SymmetricChainAi() { }

    /** A card spent, about a 1/1 token (same threshold as {@link EdictTradeAi}). */
    static final int MIN_GAIN = EdictTradeAi.MIN_GAIN;
    static final int CARD_VALUE = 100;
    static final int LIFE_VALUE = 15;

    /** The chain has at least two parts, all LoseLife / Discard / Sacrifice on "Defined$ Player", fixed amounts. */
    public static boolean applies(final SpellAbility sa) {
        if (sa == null || sa.isTrigger() || sa.usesTargeting() || sa.hasParam("AILogic")) {
            return false;
        }
        int parts = 0;
        for (SpellAbility part = sa; part != null; part = part.getSubAbility()) {
            final ApiType api = part.getApi();
            if (api != ApiType.LoseLife && api != ApiType.Discard && api != ApiType.Sacrifice) {
                return false;
            }
            if (!"Player".equals(part.getParam("Defined")) || part.usesTargeting()) {
                return false;
            }
            final String amount = amountParam(part);
            if (amount != null && !amount.matches("\\d+")) {
                return false;
            }
            parts++;
        }
        return parts >= 2;
    }

    public static AiAbilityDecision decide(final Player ai, final SpellAbility sa) {
        final Player opp = ai.getStrongestOpponent();
        if (opp == null) {
            return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
        }
        final int lifeLoss = totalLifeLoss(sa);
        if (lifeLoss >= ai.getLife() && ai.canLoseLife()) {
            return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
        }
        if (lifeLoss >= opp.getLife() && opp.canLoseLife()) {
            return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
        }
        return loss(opp, sa, false) - loss(ai, sa, true) >= MIN_GAIN
                ? new AiAbilityDecision(100, AiPlayDecision.WillPlay)
                : new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
    }

    private static int totalLifeLoss(final SpellAbility sa) {
        int sum = 0;
        for (SpellAbility part = sa; part != null; part = part.getSubAbility()) {
            if (part.getApi() == ApiType.LoseLife) {
                sum += amount(part);
            }
        }
        return sum;
    }

    /** What {@code p} loses when the whole chain resolves, parts in order. */
    static int loss(final Player p, final SpellAbility sa, final boolean caster) {
        final Card host = sa.getHostCard();
        int hand = CardLists.filter(p.getCardsIn(ZoneType.Hand), c -> !c.equals(host)).size();
        // each player sacrifices its worst creatures first
        List<Card> creatures = p.getCreaturesInPlay().stream()
                .sorted(Comparator.comparingInt(c -> c.hasSVar("SacMe") ? 0 : ComputerUtilCard.evaluateCreature(c))).toList();
        int lands = p.getLandsInPlay().size();
        int sum = 0;
        for (SpellAbility part = sa; part != null; part = part.getSubAbility()) {
            final int n = amount(part);
            if (part.getApi() == ApiType.LoseLife) {
                sum += n * (p.getLife() - n <= 5 ? 3 * LIFE_VALUE : LIFE_VALUE);
            } else if (part.getApi() == ApiType.Discard) {
                final int d = Math.min(n, hand);
                hand -= d;
                sum += d * CARD_VALUE;
            } else {
                final String valid = part.getParamOrDefault("SacValid", "Self");
                if (valid.equals("Creature")) {
                    sum += EdictTradeAi.loss(creatures, n);
                    creatures = creatures.subList(Math.min(n, creatures.size()), creatures.size());
                } else if (valid.equals("Land")) {
                    for (int i = 0; i < Math.min(n, lands); i++) {
                        // a land is worth more when few remain
                        sum += lands - i <= 3 ? 2 * CARD_VALUE : CARD_VALUE;
                    }
                    lands = Math.max(0, lands - n);
                } else {
                    sum += Math.min(n, CardLists.getValidCards(p.getCardsIn(ZoneType.Battlefield), valid,
                            sa.getActivatingPlayer(), host, part).size()) * CARD_VALUE;
                }
            }
        }
        return sum;
    }

    private static String amountParam(final SpellAbility part) {
        return switch (part.getApi()) {
            case LoseLife -> part.getParam("LifeAmount");
            case Discard -> part.getParam("NumCards");
            default -> part.getParam("Amount");
        };
    }

    private static int amount(final SpellAbility part) {
        final String s = amountParam(part);
        return s == null ? 1 : AbilityUtils.calculateAmount(part.getHostCard(), s, part);
    }
}
