package forge.ai;

import java.util.List;

import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardLists;
import forge.game.combat.Combat;
import forge.game.combat.CombatUtil;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 197 (a tester reports 03/11, ElfBall, a test game, a test game). Gilt-Leaf Archdruid 3/3
 * attacked into Black Knight and Hypnotic Specter with Heritage Druids in hand: "poser la druidesse pour piocher, on
 * n'attaque qu'à la fin". A creature that draws a card each time we play a kind of spell is worth more than its damage
 * while the hand still holds such a spell. It stays home when the possible blocks can kill it, unless two hits win.
 * An all-out or lethal attack does not come through here.
 */
public final class EngineHomeAi {
    private EngineHomeAi() { }

    public static boolean keepHome(final Player ai, final Card attacker, final List<Card> defenders, final Combat combat,
            final Player opponent) {
        if (!isFueledEngine(ai, attacker)) {
            return false;
        }
        if (opponent != null && attacker.getNetCombatDamage() * 2 >= opponent.getLife()) {
            return false;
        }
        final CardCollection blockers = CardLists.filter(defenders, b -> CombatUtil.canBlock(attacker, b));
        for (final Card b : blockers) {
            if (ComputerUtilCombat.canDestroyAttacker(ai, attacker, b, combat, false)) {
                return true;
            }
        }
        return !blockers.isEmpty()
                && ComputerUtilCombat.totalDamageOfBlockers(attacker, blockers) >= ComputerUtilCombat.getDamageToKill(attacker, false);
    }

    /** Draws when we cast or land a kind of spell, with one of them in hand. */
    static boolean isFueledEngine(final Player ai, final Card c) {
        for (final Trigger t : c.getTriggers()) {
            final boolean cast = t.getMode() == TriggerType.SpellCast;
            final boolean lands = t.getMode() == TriggerType.ChangesZone
                    && "Battlefield".equals(t.getParam("Destination"));
            if (!cast && !lands || !t.hasParam("ValidCard")) {
                continue;
            }
            if (cast && t.hasParam("ValidActivatingPlayer") && !"You".equals(t.getParam("ValidActivatingPlayer"))) {
                continue;
            }
            final SpellAbility exec = t.ensureAbility();
            if (exec == null || exec.getApi() != ApiType.Draw
                    || !"You".equals(exec.getParamOrDefault("Defined", "You"))) {
                continue;
            }
            for (final Card h : ai.getCardsIn(ZoneType.Hand)) {
                if (!h.isLand() && h.isValid(t.getParam("ValidCard").split(","), ai, c, null)) {
                    return true;
                }
            }
        }
        return false;
    }
}
