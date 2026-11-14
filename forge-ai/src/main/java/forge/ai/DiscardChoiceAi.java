package forge.ai;

import forge.game.ability.AbilityUtils;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardLists;
import forge.game.combat.CombatUtil;
import forge.game.phase.PhaseHandler;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 84 (a tester reports 29/10 15:46 and 15:55, Gwyll - Vampire, turn 5). Gwyll attacked with a 7/7
 * Nightsky Mimic, then cast Humiliate in main 2 and took Vampire Nighthawk, leaving Cast Down and Gatekeeper of
 * Malakir in hand: the Mimic died on the next turn.
 * General rules:
 * a) when the AI picks the card an opponent discards, a card in the opponent's hand that would remove its best creature
 *    (destroy, exile, bounce, sacrifice, damage at least its toughness, as a spell or as an enters trigger) comes first;
 * b) a discard spell cast from hand on the AI's own turn no longer waits for main 2 when the AI has a creature that can
 *    attack this turn: the hand is stripped before the attack (removal, combat tricks) and riders such as +1/+1 counters
 *    count in combat.
 */
public final class DiscardChoiceAi {
    private DiscardChoiceAi() { }

    /** a) The card of the discarder's hand that answers the chooser's best creature, or null. */
    public static Card answerToOurThreat(final Player chooser, final Player discarder, final CardCollection choices) {
        final Card threat = ComputerUtilCard.getBestCreatureAI(chooser.getCreaturesInPlay());
        if (threat == null) {
            return null;
        }
        final CardCollection answers = CardLists.filter(choices,
                c -> !c.isLand() && answers(c, threat, chooser, discarder));
        return answers.isEmpty() ? null : ComputerUtilCard.getBestAI(answers);
    }

    static boolean answers(final Card c, final Card threat, final Player chooser, final Player discarder) {
        for (final SpellAbility sa : c.getSpells()) {
            if (removes(sa, threat, chooser, discarder)) {
                return true;
            }
        }
        if (c.isPermanent()) {
            for (final Trigger t : c.getTriggers()) {
                if (t.getMode() != TriggerType.ChangesZone || !"Battlefield".equals(t.getParam("Destination"))
                        || t.getParam("ValidCard") == null || !t.getParam("ValidCard").startsWith("Card.Self")) {
                    continue;
                }
                final SpellAbility tsa = t.ensureAbility();
                if (tsa != null && removes(tsa, threat, chooser, discarder)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean removes(final SpellAbility first, final Card threat, final Player chooser, final Player discarder) {
        for (SpellAbility sa = first; sa != null; sa = sa.getSubAbility()) {
            final ApiType api = sa.getApi();
            if (api == null) {
                continue;
            }
            switch (api) {
                case Destroy:
                case ChangeZone:
                case DealDamage:
                case Pump:
                    break;
                case Sacrifice:
                    // an edict only answers the threat when it is the chooser's only creature
                    if (chooser.getCreaturesInPlay().size() == 1 && (sa.usesTargeting()
                            ? sa.getTargetRestrictions().canTgtPlayer() : sa.hasParam("Defined")
                              && sa.getParam("Defined").contains("Opponent"))) {
                        return true;
                    }
                    continue;
                default:
                    continue;
            }
            if (!sa.usesTargeting() || !sa.getTargetRestrictions().canTgtCreature()) {
                continue;
            }
            sa.setActivatingPlayer(discarder);
            if (!threat.isValid(sa.getTargetRestrictions().getValidTgts(), discarder, sa.getHostCard(), sa)) {
                continue;
            }
            if (api == ApiType.ChangeZone && !"Battlefield".equals(sa.getParam("Origin"))) {
                continue;
            }
            if (api == ApiType.DealDamage) {
                final int dmg = AbilityUtils.calculateAmount(sa.getHostCard(), sa.getParamOrDefault("NumDmg", "0"), sa);
                if (dmg < threat.getNetToughness()) {
                    continue;
                }
            }
            if (api == ApiType.Pump) {
                final String nt = sa.getParam("NumDef");
                if (nt == null || !nt.startsWith("-")) {
                    continue;
                }
                if (-AbilityUtils.calculateAmount(sa.getHostCard(), nt, sa) < threat.getNetToughness()) {
                    continue;
                }
            }
            return true;
        }
        return false;
    }

    /** b) A discard spell from hand may be cast before combat on the AI's own turn when an attack is possible. */
    /** magic-tools: rule 229, our hand is discarded for at least five new cards: the sooner, the more of them we can use. */
    public static boolean isWheelSpell(final Player ai, final SpellAbility sa) {
        if (!sa.isSpell() || !"Hand".equals(sa.getParam("Mode")) || sa.usesTargeting()
                || !forge.game.ability.AbilityUtils.getDefinedPlayers(sa.getHostCard(), sa.getParam("Defined"), sa).contains(ai)) {
            return false;
        }
        for (SpellAbility sub = sa.getSubAbility(); sub != null; sub = sub.getSubAbility()) {
            if (sub.getApi() == forge.game.ability.ApiType.Draw && !sub.usesTargeting()) {
                final String num = sub.getParamOrDefault("NumCards", "1");
                return num.matches("\\d+") && Integer.parseInt(num) >= 5;
            }
        }
        return false;
    }

    public static boolean castBeforeAttack(final Player ai, final SpellAbility sa) {
        final Card source = sa.getHostCard();
        final PhaseHandler ph = ai.getGame().getPhaseHandler();
        if (source == null || !sa.isSpell() || !source.isInZone(ZoneType.Hand) || !ph.isPlayerTurn(ai)
                || !ph.getPhase().isBefore(PhaseType.COMBAT_DECLARE_ATTACKERS)) {
            return false;
        }
        for (final Card c : ai.getCreaturesInPlay()) {
            if (CombatUtil.canAttack(c)) {
                return true;
            }
        }
        return false;
    }
}
