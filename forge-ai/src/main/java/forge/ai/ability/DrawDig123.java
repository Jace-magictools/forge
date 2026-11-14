package forge.ai.ability;

import java.util.ArrayList;
import java.util.List;

import forge.game.CardTraitBase;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CardCollectionView;
import forge.game.card.CardLists;
import forge.game.player.Player;
import forge.game.replacement.ReplacementEffect;
import forge.game.replacement.ReplacementType;
import forge.game.spellability.AbilitySub;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 123. An optional "if you would draw a card, instead reveal until you reveal a card of the chosen
 * kind and put it into your hand" (Pavel Chekov, First Posting; Abundance) was always refused: the replacement is a
 * GenericChoice without AILogic, whose AI says CantPlayAi. It is a draw that picks its kind, never worse than the draw,
 * so accept it whenever the library holds a card of some offered kind, and choose land or nonland from the hand:
 * land when no land is in hand and fewer than 4 lands are in play (or the hand holds spells we cannot cast yet),
 * nonland otherwise.
 */
public final class DrawDig123 {
    private DrawDig123() { }

    /** TRUE: accept the replacement. NULL: not concerned. */
    public static Boolean accept(CardTraitBase effect, SpellAbility sa, Player ai) {
        if (!(effect instanceof ReplacementEffect) || sa == null) {
            return null;
        }
        ReplacementEffect re = (ReplacementEffect) effect;
        if (re.getMode() != ReplacementType.Draw) {
            return null;
        }
        List<SpellAbility> digs = digChoices(sa);
        if (digs == null) {
            return null;
        }
        for (SpellAbility dig : digs) {
            if (!matches(ai, dig).isEmpty()) {
                return Boolean.TRUE;
            }
        }
        return null;
    }

    /** The choice to take among GenericChoice options that are all "dig until → hand", or null. */
    public static SpellAbility pick(Player ai, List<SpellAbility> spells) {
        if (spells == null || spells.size() < 2) {
            return null;
        }
        for (SpellAbility sp : spells) {
            if (!isDigToHand(sp)) {
                return null;
            }
        }
        boolean wantLand = wantsLand(ai);
        SpellAbility best = null;
        for (SpellAbility sp : spells) {
            if (matches(ai, sp).isEmpty()) {
                continue;
            }
            boolean landChoice = isLandOnly(sp);
            if (landChoice == wantLand) {
                return sp;
            }
            if (best == null) {
                best = sp;
            }
        }
        return best;
    }

    static boolean wantsLand(Player ai) {
        CardCollectionView hand = ai.getCardsIn(ZoneType.Hand);
        int landsInHand = CardLists.count(hand, Card::isLand);
        if (landsInHand > 0) {
            return false;
        }
        int lands = ai.getLandsInPlay().size();
        if (lands < 4) {
            return true;
        }
        for (Card c : hand) {
            if (!c.isLand() && c.getCMC() > lands) {
                return true;
            }
        }
        return false;
    }

    private static List<SpellAbility> digChoices(SpellAbility sa) {
        List<SpellAbility> out = new ArrayList<>();
        if (sa.getApi() == ApiType.GenericChoice) {
            for (AbilitySub sub : sa.getAdditionalAbilityList("Choices")) {
                if (!isDigToHand(sub)) {
                    return null;
                }
                out.add(sub);
            }
        } else if (isDigToHand(sa)) {
            out.add(sa);
        }
        return out.isEmpty() ? null : out;
    }

    private static boolean isDigToHand(SpellAbility sa) {
        return sa.getApi() == ApiType.DigUntil && "Hand".equals(sa.getParam("FoundDestination"))
                && sa.hasParam("Valid") && !sa.hasParam("Amount") && sa.getSubAbility() == null
                && !"Graveyard".equals(sa.getParam("RevealedDestination"))
                && !"Exile".equals(sa.getParam("RevealedDestination"));
    }

    private static boolean isLandOnly(SpellAbility sa) {
        String valid = sa.getParam("Valid");
        return valid.startsWith("Card.Land") || valid.equals("Land");
    }

    private static CardCollectionView matches(Player ai, SpellAbility dig) {
        return CardLists.getValidCards(ai.getCardsIn(ZoneType.Library), dig.getParam("Valid"), ai, dig.getHostCard(), dig);
    }
}
