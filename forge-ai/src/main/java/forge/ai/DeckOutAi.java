package forge.ai;

import java.util.Map;

import com.google.common.collect.Maps;

import forge.game.Game;
import forge.game.ability.AbilityKey;
import forge.game.ability.AbilityUtils;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;
import forge.game.trigger.TriggerWaiting;
import forge.game.zone.ZoneType;

/**
 * Mandatory "whenever you cast ..., draw a card" (Beast Whisperer, Glimpse of Nature): the AI kept casting creatures
 * until its library was empty. A spell whose forced draws would empty the library is not cast.
 */
public final class DeckOutAi {
    private DeckOutAi() {
    }

    /** magic-tools, règle 17 : cards kept in the library when an optional repeat digs it (Primal Surge). */
    static final int REPEAT_KEEP_LIBRARY = 3;

    /**
     * magic-tools, règle 17 : an optional step of a Repeat that digs our own library ("exile the top card, you may
     * put it onto the battlefield; if you do, repeat": Primal Surge) was always accepted, so Wurm put its whole
     * library onto the battlefield and lost on its next draw. Stop once the library is down to a few cards.
     */
    public static boolean optionalRepeatEmptiesLibrary(final Player ai, final SpellAbility sa) {
        if (sa == null || !sa.hasParam("Optional")) {
            return false;
        }
        SpellAbility repeat = sa.getParent();
        while (repeat != null && repeat.getApi() != ApiType.Repeat) {
            repeat = repeat.getParent();
        }
        if (repeat == null || !digsOwnLibrary(repeat.getAdditionalAbility("RepeatSubAbility"))) {
            return false;
        }
        // the draws of "whenever a creature enters, draw a card" (The Great Henge, Soul of the Harvest) only come
        // once the repeat is over: count those already owed, and those the next creature would add
        return ai.getCardsIn(ZoneType.Library).size()
                <= REPEAT_KEEP_LIBRARY + pendingEnterDraws(ai) + enterDrawsPerCreature(ai);
    }

    /** Draws owed to us by "enters the battlefield" triggers held until the current resolution ends. */
    static int pendingEnterDraws(final Player ai) {
        final Game game = ai.getGame();
        int draws = 0;
        for (TriggerWaiting wt : game.getTriggerHandler().getWaitingTriggers()) {
            if (wt.getMode() != TriggerType.ChangesZone) {
                continue;
            }
            for (Trigger t : game.getTriggerHandler().getActiveTrigger(wt.getMode(), wt.getParams())) {
                draws += drawsOf(ai, t);
            }
        }
        return draws;
    }

    /** Draws one more creature entering under our control would give (optional draws counted: we stay prudent). */
    static int enterDrawsPerCreature(final Player ai) {
        int draws = 0;
        for (Card c : ai.getCardsIn(ZoneType.Battlefield)) {
            for (Trigger t : c.getTriggers()) {
                if (t.getMode() == TriggerType.ChangesZone && "Battlefield".equals(t.getParam("Destination"))
                        && t.getParamOrDefault("ValidCard", "").contains("Creature")) {
                    draws += drawsOf(ai, t);
                }
            }
        }
        return draws;
    }

    private static int drawsOf(final Player ai, final Trigger t) {
        final Card host = t.getHostCard();
        if (host == null || !ai.equals(host.getController())) {
            return 0;
        }
        for (SpellAbility sub = t.ensureAbility(); sub != null; sub = sub.getSubAbility()) {
            if (sub.getApi() == ApiType.Draw && "You".equals(sub.getParamOrDefault("Defined", "You"))) {
                try {
                    return AbilityUtils.calculateAmount(host, sub.getParamOrDefault("NumCards", "1"), sub);
                } catch (RuntimeException e) {
                    return 1;
                }
            }
        }
        return 0;
    }

    private static boolean digsOwnLibrary(SpellAbility sub) {
        for (; sub != null; sub = sub.getSubAbility()) {
            if (sub.getApi() == ApiType.Dig && "You".equals(sub.getParamOrDefault("Defined", "You"))
                    && "Library".equals(sub.getParamOrDefault("SourceZone", "Library"))) {
                return true;
            }
        }
        return false;
    }

    public static boolean wouldDeckSelf(final Player ai, final SpellAbility sa) {
        if (!sa.isSpell() || sa.getHostCard() == null) {
            return false;
        }
        final int library = ai.getCardsIn(ZoneType.Library).size();
        return forcedDrawsOnCast(ai, sa) >= Math.max(library, 1);
    }

    static int forcedDrawsOnCast(final Player ai, final SpellAbility sa) {
        final Game game = ai.getGame();
        final Map<AbilityKey, Object> runParams = Maps.newHashMap();
        runParams.put(AbilityKey.SpellAbility, sa);
        runParams.put(AbilityKey.Card, sa.getHostCard());
        runParams.put(AbilityKey.Activator, ai);

        int draws = 0;
        for (Card c : ai.getCardsIn(ZoneType.listValueOf("Battlefield,Command"))) {
            for (Trigger t : c.getTriggers()) {
                if (t.getMode() != TriggerType.SpellCast || t.hasParam("OptionalDecider")
                        || !t.zonesCheck(game.getZoneOf(c))) {
                    continue;
                }
                final SpellAbility exec = t.ensureAbility();
                if (exec == null || exec.getApi() != ApiType.Draw || exec.hasParam("Cost")
                        || !"You".equals(exec.getParamOrDefault("Defined", "You"))) {
                    continue;
                }
                try {
                    if (!t.performTest(runParams)) {
                        continue;
                    }
                } catch (RuntimeException e) {
                    continue;
                }
                draws += AbilityUtils.calculateAmount(c, exec.getParamOrDefault("NumCards", "1"), exec);
            }
        }
        return draws;
    }
}
