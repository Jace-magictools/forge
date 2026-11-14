package forge.ai;

import java.util.List;

import forge.game.ability.AbilityUtils;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.combat.CombatUtil;
import forge.game.keyword.Keyword;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.staticability.StaticAbility;
import forge.game.staticability.StaticAbilityMode;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 110 (a tester report 30/10 12:04, Kavaron - Profanation, turn 3): Rabbit Battery in play, Kavaron
 * Harrier just cast, one red mana left: the AI cast Rust Harvester and the Battery attacked alone for 1, instead of
 * reconfiguring it onto the Harrier (haste, 3/2) for 3.
 * <p>In our main 1, an equip / reconfigure ability of a permanent that gives haste to the equipped creature goes
 * before the other spells when one of our creatures cannot attack this turn only because of summoning sickness, its
 * attack with the bonus brings more than the attachment does now (the Battery's own 1 power, or the creature it
 * already equips), and no untapped creature of the opponent can block and kill it. AttachAi chooses that creature
 * (pump logic: sick creatures with haste granted first).</p>
 */
public final class HasteAttachAi {
    private HasteAttachAi() { }

    public static void prioritize(final Player ai, final List<SpellAbility> all) {
        if (!ai.getGame().getPhaseHandler().is(PhaseType.MAIN1, ai) || !ai.getGame().getStack().isEmpty()) {
            return;
        }
        for (int i = 1; i < all.size(); i++) {
            if (wantsHasteNow(ai, all.get(i))) {
                all.add(0, all.remove(i));
                return;
            }
        }
    }

    public static boolean wantsHasteNow(final Player ai, final SpellAbility sa) {
        if (sa.getApi() != ApiType.Attach || sa.isSpell() || !sa.usesTargeting()) {
            return false;
        }
        final Card host = sa.getHostCard();
        if (host == null || !host.isInPlay() || !host.isEquipment()) {
            return false;
        }
        int power = 0, toughness = 0;
        boolean haste = false;
        for (StaticAbility st : host.getStaticAbilities()) {
            if (!st.checkMode(StaticAbilityMode.Continuous) || !"Equipped".equals(st.getParam("AffectedDefined"))) {
                continue;
            }
            if (st.hasParam("AddPower")) {
                power += AbilityUtils.calculateAmount(host, st.getParam("AddPower"), st);
            }
            if (st.hasParam("AddToughness")) {
                toughness += AbilityUtils.calculateAmount(host, st.getParam("AddToughness"), st);
            }
            if (st.hasParam("AddKeyword") && st.getParam("AddKeyword").contains("Haste")) {
                haste = true;
            }
        }
        if (!haste) {
            return false;
        }
        // what the attachment brings to this combat as it is
        int now = 0;
        final Card equipped = host.getAttachedTo();
        if (equipped != null && equipped.isCreature()) {
            if (CombatUtil.canAttack(equipped)) {
                return false;
            }
        } else if (host.isCreature() && CombatUtil.canAttack(host)) {
            now = host.getNetPower();
        }
        for (Card c : ai.getCreaturesInPlay()) {
            if (c == host || !c.isSick() || c.hasKeyword(Keyword.DEFENDER) || !sa.canTarget(c)) {
                continue;
            }
            final int p = c.getNetPower() + power;
            if (p <= now || !safeAttack(ai, c, toughness)) {
                continue;
            }
            return true;
        }
        return false;
    }

    private static boolean safeAttack(final Player ai, final Card attacker, final int toughnessBonus) {
        final int t = attacker.getNetToughness() + toughnessBonus;
        for (Player opp : ai.getOpponents()) {
            for (Card b : opp.getCardsIn(ZoneType.Battlefield)) {
                if (b.isCreature() && b.isUntapped() && CombatUtil.canBlock(attacker, b)
                        && (b.getNetPower() >= t || b.hasKeyword(Keyword.DEATHTOUCH))) {
                    return false;
                }
            }
        }
        return true;
    }
}
