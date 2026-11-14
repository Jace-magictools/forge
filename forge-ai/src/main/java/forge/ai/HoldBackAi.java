package forge.ai;

import forge.card.CardType;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CardLists;
import forge.game.keyword.Keyword;
import forge.game.phase.PhaseHandler;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.staticability.StaticAbility;
import forge.game.staticability.StaticAbilityMode;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 93 (a tester report 29/10 11:41, Auriok - Aragorn, turn 7): Auriok already had Vault Skirge
 * and two Auriok Edgewright against an empty board and still cast Sunspear Shikari in main 2, all its creatures
 * exposed to a sweeper. Stock AI casts every creature it can. General rule: a plain creature (no enter trigger, no
 * effect on the other cards, no mana, not bigger than what is already there) stays in hand when the board is
 * already won on width: three creatures or more, two more than the opponent, an opponent who has the lands for a
 * sweeper (four) and cards in hand, and no anthem in our deck (going wide is then the plan). It comes down as soon
 * as the board shrinks (the rebuild after the sweeper) or the hand would overflow.
 */
public final class HoldBackAi {
    private HoldBackAi() { }

    private static final int MIN_CREATURES = 3;
    private static final int LEAD = 2;
    private static final int SWEEPER_LANDS = 4;

    public static boolean holdBack(final Player ai, final SpellAbility sa, final PhaseHandler ph) {
        final Card card = sa.getHostCard();
        if (card == null || !sa.isSpell() || !card.isCreature() || !card.isInZone(ZoneType.Hand) || !ph.isPlayerTurn(ai)) {
            return false;
        }
        if (card.hasKeyword(Keyword.FLASH) || card.hasETBTrigger(true) || ManaBaseFirstAi.simpleManaSource(card)
                || affectsOthers(card) || !card.getManaAbilities().isEmpty()) {
            return false;
        }
        if (card.hasKeyword(Keyword.HASTE) && ph.getPhase().isBefore(PhaseType.COMBAT_DECLARE_ATTACKERS)) {
            return false;
        }
        // magic-tools: rule 158, a lifelink body is not a spare one when we are behind on life (banc a test game,
        // Nip Gwyllion at 11 life against 17): the life it gains back is worth more than the cover against a sweeper
        if (card.hasKeyword(Keyword.LIFELINK)) {
            for (final Player opp : ai.getOpponents()) {
                if (ai.getLife() < opp.getLife()) {
                    return false;
                }
            }
        }
        // magic-tools: rule 169 (relevé « mana perdu » v207, Gwyll - Chevalier t12-24 : Chevalier à 4 vies gardait
        // Worthy Knight et Inspiring Veteran six tours avec 7 terrains) : un sort qui déclenche un de nos permanents
        // (« whenever you cast a Knight spell ») n'est pas un corps de réserve, il rapporte en arrivant
        if (firesCastTrigger(ai, card)) {
            return false;
        }
        final int mine = ai.getCreaturesInPlay().size();
        if (mine < MIN_CREATURES) {
            return false;
        }
        int maxPower = 0;
        for (final Card c : ai.getCreaturesInPlay()) {
            maxPower = Math.max(maxPower, c.getNetPower());
        }
        if (card.getNetPower() > maxPower) {
            return false;
        }
        // an anthem or a lord in play or in hand: the deck plays wide
        for (final Card c : ai.getCardsIn(ZoneType.Battlefield)) {
            if (affectsOthers(c)) {
                return false;
            }
        }
        for (final Card c : ai.getCardsIn(ZoneType.Hand)) {
            if (c != card && affectsOthers(c)) {
                return false;
            }
        }
        // the hand would overflow at cleanup: better on the battlefield than in the graveyard
        if (!ai.isUnlimitedHandSize() && ai.getCardsIn(ZoneType.Hand).size() > ai.getMaxHandSize()) {
            return false;
        }
        boolean sweeperRisk = false;
        for (final Player opp : ai.getOpponents()) {
            if (mine < opp.getCreaturesInPlay().size() + LEAD) {
                return false;
            }
            // the opponent is about to die: every attacker counts
            int power = 0;
            for (final Card c : ai.getCreaturesInPlay()) {
                power += Math.max(0, c.getNetCombatDamage());
            }
            if (opp.getLife() <= power + card.getNetCombatDamage()) {
                return false;
            }
            if (CardLists.filter(opp.getCardsIn(ZoneType.Battlefield), Card::isLand).size() >= SWEEPER_LANDS
                    && !opp.getCardsIn(ZoneType.Hand).isEmpty()) {
                sweeperRisk = true;
            }
        }
        return sweeperRisk;
    }

    /** rule 169: one of our permanents triggers when we cast this spell (Worthy Knight, prowess-like payoffs) */
    static boolean firesCastTrigger(final Player ai, final Card card) {
        for (final Card p : ai.getCardsIn(ZoneType.Battlefield)) {
            for (final Trigger t : p.getTriggers()) {
                if (t.getMode() != TriggerType.SpellCast && t.getMode() != TriggerType.SpellCastOrCopy) {
                    continue;
                }
                final String who = t.getParam("ValidActivatingPlayer");
                if (who != null && !"You".equals(who)) {
                    continue;
                }
                final String valid = t.getParam("ValidCard");
                if (valid != null && card.isValid(valid.split(","), ai, p, t)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * a continuous effect on other cards (anthem, lord, cost reduction for the others...). Rule 169: a tribal lord
     * names its creature type, not « Creature » (Inspiring Veteran, Affected$ Knight.YouCtrl+Other).
     */
    static boolean affectsOthers(final Card c) {
        if (pumpsTeamOnAttack(c)) {
            return true;
        }
        for (final StaticAbility sta : c.getStaticAbilities()) {
            if (!sta.getMode().contains(StaticAbilityMode.Continuous)) {
                continue;
            }
            if (sta.hasParam("AffectedDefined")) {
                continue;
            }
            final String affected = sta.getParam("Affected");
            if (affected != null && (affected.contains("Creature") || isCreatureTypeFilter(affected))
                    && !affected.startsWith("Card.Self")
                    && !affected.contains("EnchantedBy") && !affected.contains("EquippedBy") && !affected.contains("AttachedBy")) {
                return true;
            }
        }
        return false;
    }

    /**
     * rule 226 (relevé « mana perdu » v259, Gobelin - merfolk t9-13 : Signal Pest gardé trois tours avec trois Montagnes
     * dégagées, deux Gobelins et un Arsonist qui attaquent) : a body that pumps the others when it attacks (battle cry,
     * mentor, « whenever this attacks, creatures get ») is an anthem for the turn, not a spare one.
     */
    private static boolean pumpsTeamOnAttack(final Card c) {
        if (c.hasKeyword(Keyword.BATTLE_CRY) || c.hasKeyword(Keyword.MENTOR)) {
            return true;
        }
        for (final Trigger t : c.getTriggers()) {
            if (t.getMode() != TriggerType.Attacks || !"Card.Self".equals(t.getParam("ValidCard"))) {
                continue;
            }
            final SpellAbility exec = t.ensureAbility();
            if (exec != null && (exec.getApi() == ApiType.PumpAll || exec.getApi() == ApiType.PutCounterAll)) {
                return true;
            }
        }
        return false;
    }

    /** « Knight.YouCtrl+Other », « Elf.Other » : each filter starts with a creature type */
    private static boolean isCreatureTypeFilter(final String affected) {
        for (final String part : affected.split(",")) {
            final String head = part.split("[.+]")[0];
            if (CardType.isACreatureType(head)) {
                return true;
            }
        }
        return false;
    }
}
