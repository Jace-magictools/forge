package forge.ai;

import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CardCollectionView;
import forge.game.card.CardLists;
import forge.game.card.CardPredicates;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 20, one-sided land wipe. Armageddon in a deck of indestructible lands (Bridges, Darksteel
 * Citadel) was refused whenever the opponent had a better board of creatures (DestroyAllAi, lands-only case):
 * the AI kept its mana and let the opponent keep theirs.
 * General rule: a "destroy all lands" that leaves us at least three mana-producing lands and the opponent with
 * at most one (three fewer than us) is cast, unless the opponent's creatures already threaten us: their total
 * power must stay under half our life, or we hold another sweeper (DestroyAll / DamageAll of creatures) to clean up afterwards.
 * Rule 64 (a tester report, 29/10): that backup sweeper must be payable with the lands we keep. At 8 life against
 * 7 power of Rabbits, Armageddon was cast "because of" Jokulhaups (6 mana) with 4 Bridges left, instead of Ghostly Prison.
 */
public final class LandWipeAi {
    private LandWipeAi() { }

    public static boolean oneSided(final Player ai, final Player opponent, final CardCollectionView aiDestroyed,
            final CardCollectionView oppDestroyed, final Card source) {
        final int ours = CardLists.count(ai.getCardsIn(ZoneType.Battlefield),
                CardPredicates.LANDS_PRODUCING_MANA.and(c -> !aiDestroyed.contains(c)));
        final int theirs = CardLists.count(opponent.getLandsInPlay(), c -> !oppDestroyed.contains(c));
        if (ours < 3 || theirs > 1 || ours < theirs + 3 || oppDestroyed.size() < 2) {
            return false;
        }
        int power = 0;
        for (final Card c : opponent.getCreaturesInPlay()) {
            power += Math.max(0, c.getNetCombatDamage());
        }
        return power * 2 < ai.getLife() || holdsSweeper(ai, source, ours);
    }

    private static boolean holdsSweeper(final Player ai, final Card source, final int landsKept) {
        for (final Card c : ai.getCardsIn(ZoneType.Hand)) {
            if (c == source || c.getCMC() > landsKept) {
                continue;
            }
            for (final SpellAbility sa : c.getSpellAbilities()) {
                if ((sa.getApi() == ApiType.DestroyAll || sa.getApi() == ApiType.DamageAll)
                        && sa.getParamOrDefault("ValidCards", "").contains("Creature")) {
                    return true;
                }
            }
        }
        return false;
    }
}
