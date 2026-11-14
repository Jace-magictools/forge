package forge.ai;

import forge.card.CardType;
import forge.card.MagicColor;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardLists;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 38, a fetchland is worth the lands it can find. Stock AiController.chooseBestLandToPlay scored
 * a land by its own mana abilities ("TODO handle fetchlands"): Evolving Wilds or Marsh Flats scored 13, any basic
 * 100 more, so the fetch stayed in hand while a basic came down, even with nothing to cast (Genesis 27/10: Wilds
 * kept on turns 1 and 2, Island played; Profanation: Plains on turn 4, Marsh Flats kept). Now a fetch counts as
 * one mana of the best color it can still find in the library, a new basic type included (on turn 1 with
 * nothing to cast, Wilds 191 against Island 181). When a spell in hand needs the land drop this turn,
 * rule 21 already keeps only the untapped lands that produce mana, so the basic still comes first there.
 */
public final class FetchLandPlayAi {
    private FetchLandPlayAi() { }

    /** Extra score for {@code land} if it is a fetchland, given the mana counts in WUBRGC order; 0 otherwise. */
    public static int fetchScore(final Player ai, final Card land, final int[] counts) {
        final CardCollection found = fetchable(ai, land);
        if (found.isEmpty()) {
            return 0;
        }
        int best = 0;
        boolean newType = false;
        for (final Card c : found) {
            for (final String type : c.getType().getLandTypes()) {
                if (CardType.isABasicLandType(type)
                        && !ai.getLandsInPlay().anyMatch(l -> l.getType().hasSubtype(type))) {
                    newType = true;
                }
            }
            byte mask = 0;
            for (final String color : c.getProducibleColors()) {
                mask |= MagicColor.fromName(color);
            }
            for (int i = 0; i < MagicColor.WUBRG.length; i++) {
                if ((mask & MagicColor.WUBRG[i]) != 0) {
                    best = Math.max(best, 50 / (counts[i] + 1));
                }
            }
        }
        // one mana like a basic (evaluateLand: 100 per mana, 3 per color), plus the best color it brings, plus the
        // new basic type bonus a basic would get: a fetch that can find it ties a basic with it, and comes first
        // since it keeps the choice for later and thins the library
        return 103 + best + (newType ? 25 : 0);
    }

    /** The lands of the library that a land's own search ability can put onto the battlefield. */
    static CardCollection fetchable(final Player ai, final Card land) {
        for (final SpellAbility sa : land.getNonManaAbilities()) {
            if (sa.getApi() != ApiType.ChangeZone || !"Library".equals(sa.getParam("Origin"))
                    || !"Battlefield".equals(sa.getParam("Destination")) || !sa.hasParam("ChangeType")
                    || sa.getPayCosts() == null || sa.getPayCosts().getTotalMana().getCMC() > 0) {
                continue;
            }
            sa.setActivatingPlayer(ai);
            final CardCollection found = CardLists.getValidCards(ai.getCardsIn(ZoneType.Library),
                    sa.getParam("ChangeType").split(","), ai, land, sa);
            found.removeIf(c -> !c.isLand());
            if (!found.isEmpty()) {
                return found;
            }
        }
        return new CardCollection();
    }
}
