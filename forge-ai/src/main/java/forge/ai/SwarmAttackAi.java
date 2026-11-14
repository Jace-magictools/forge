package forge.ai;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import forge.game.GameEntity;
import forge.game.card.Card;
import forge.game.card.CardLists;
import forge.game.combat.CombatUtil;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 156 (banc « IA contre décisions humaines », a test game, 03/11). Two 1/1 Soldier tokens
 * facing a deck of flyers stayed home: they cannot block anything the opponent has, so staying back defends nothing,
 * and the opponent (tapped out) had a single untapped blocker. A human swarms: one token may die, the rest connect.
 * Attack with a small creature when it cannot block any opponent creature, and the small creatures in the same
 * situation outnumber the opponent's possible blockers (plus one if they hold up mana for a flash blocker or a trick),
 * so that at least one point of damage gets through whatever they block.
 */
public final class SwarmAttackAi {
    /** Up to a 2/2 with a keyword, or a token: what a human throws in without a second thought. */
    static final int SMALL_VALUE = 150;

    private SwarmAttackAi() { }

    static boolean uselessBlocker(final Card c, final Player opp) {
        for (final Card o : opp.getCreaturesInPlay()) {
            if (CombatUtil.canBlock(o, c, true)) {
                return false;
            }
        }
        return true;
    }

    static boolean swarmAttacker(final Player ai, final GameEntity defender, final Card attacker, final List<Card> candidates) {
        if (!(defender instanceof Player) || !((Player) defender).isOpponentOf(ai)) {
            return false;
        }
        final Player opp = (Player) defender;
        if (opp.getCreaturesInPlay().isEmpty() || !candidates.contains(attacker)) {
            return false;
        }
        final List<Card> swarm = new ArrayList<>();
        for (final Card c : candidates) {
            if (c.getNetCombatDamage() > 0 && ComputerUtilCard.evaluateCreature(c) <= SMALL_VALUE && uselessBlocker(c, opp)) {
                swarm.add(c);
            }
        }
        if (!swarm.contains(attacker)) {
            return false;
        }
        int blockers = 0;
        for (final Card b : opp.getCreaturesInPlay()) {
            for (final Card a : swarm) {
                if (CombatUtil.canBlock(a, b)) {
                    blockers++;
                    break;
                }
            }
        }
        if (CardLists.count(opp.getCardsIn(ZoneType.Battlefield), c -> c.isLand() && !c.isTapped()) >= 2) {
            blockers++;
        }
        if (swarm.size() <= blockers) {
            return false;
        }
        swarm.sort(Comparator.comparingInt(Card::getNetCombatDamage).reversed());
        int through = 0;
        for (int i = blockers; i < swarm.size(); i++) {
            through += swarm.get(i).getNetCombatDamage();
        }
        return through >= 1;
    }
}
