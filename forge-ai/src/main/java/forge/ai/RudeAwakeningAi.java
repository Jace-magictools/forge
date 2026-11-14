package forge.ai;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import forge.game.card.Card;
import forge.game.card.CardLists;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/**
 * magic-tools: Rude Awakening (Nissa, AILogic$ RudeAwakening). A finisher: the deck piles up lands, then pays
 * the entwine (8 mana) in its first main phase and attacks with every land as an untapped 2/2. Never one mode
 * alone (tester, 24/10). Forge marked it AI:RemoveDeck:All, CharmAi alone never chose both modes.
 * Cast only if the attack is lethal against the weakest opponent, each untapped opposing creature
 * blocking one of our biggest attackers.
 */
public final class RudeAwakeningAi {
    private RudeAwakeningAi() { }

    public static boolean applies(final SpellAbility sa) {
        return "RudeAwakening".equals(sa.getParam("AILogic"));
    }

    public static boolean shouldCast(final Player ai, final SpellAbility sa) {
        if (!sa.isEntwine() || !ai.getGame().getPhaseHandler().is(PhaseType.MAIN1, ai)) {
            return false;
        }
        final Player opp = ai.getWeakestOpponent();
        if (opp == null) {
            return false;
        }
        final List<Integer> powers = new ArrayList<>();
        for (final Card land : ai.getCardsIn(ZoneType.Battlefield)) {
            if (land.isLand() && !land.isCreature() && !land.hasSickness()) {
                powers.add(2);
            }
        }
        for (final Card c : ai.getCreaturesInPlay()) {
            if (c.isUntapped() && !c.hasSickness() && !c.isLand()) {
                powers.add(c.getNetCombatDamage());
            }
        }
        final int blockers = CardLists.filter(opp.getCreaturesInPlay(), Card::isUntapped).size();
        powers.sort(Comparator.reverseOrder());
        int damage = 0;
        for (int k = blockers; k < powers.size(); k++) {
            damage += powers.get(k);
        }
        return damage >= opp.getLife();
    }
}
