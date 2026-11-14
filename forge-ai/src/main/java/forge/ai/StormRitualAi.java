package forge.ai;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import forge.game.ability.ApiType;
import forge.game.phase.PhaseHandler;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 108, ritual mana goes to the spell it was cast for (Gobelin - Patrilock, forge-v129, turn 17:
 * three Mountains, Pyretic Ritual, Empty the Warrens and Mass Hysteria in hand; the ritual's {R}{R}{R} paid
 * Mass Hysteria and three mana were lost instead of four goblins; the same deck kept Empty the Warrens in hand
 * for twenty turns).
 * <ul>
 * <li>a token sorcery cast from the hand is not subject to the random TOKEN_GENERATION_ABILITY_CHANCE (meant for
 * repeatable abilities): the ritual's own check and the spell itself drew it apart, one time in five each;</li>
 * <li>with mana floating in our pool (a ritual), the spells of the hand are tried by decreasing mana value;</li>
 * <li>a Storm spell skipped while other spells are in hand (MIN_COUNT_FOR_STORM_SPELLS) is tried when none of them
 * is played (see AiController.chooseSpellAbilityToPlayFromList).</li>
 * </ul>
 */
public final class StormRitualAi {
    private StormRitualAi() { }

    public static boolean noChanceRoll(final Player ai, final SpellAbility sa) {
        return sa.isSpell() && sa.getApi() == ApiType.Token && sa.getHostCard().isSorcery()
                && ai.getCardsIn(ZoneType.Hand).contains(sa.getHostCard());
    }

    public static void prioritize(final Player ai, final List<SpellAbility> all) {
        final PhaseHandler ph = ai.getGame().getPhaseHandler();
        if (!ph.isPlayerTurn(ai) || !ph.getPhase().isMain() || ai.getManaPool().totalMana() <= 0) {
            return;
        }
        final List<Integer> slots = new ArrayList<>();
        final List<SpellAbility> spells = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            final SpellAbility sa = all.get(i);
            if (sa.isSpell() && ai.getCardsIn(ZoneType.Hand).contains(sa.getHostCard())) {
                slots.add(i);
                spells.add(sa);
            }
        }
        if (spells.size() < 2) {
            return;
        }
        spells.sort(Comparator.comparingInt((SpellAbility sa) -> sa.getPayCosts().getTotalMana().getCMC()).reversed());
        for (int k = 0; k < slots.size(); k++) {
            all.set(slots.get(k), spells.get(k));
        }
    }
}
