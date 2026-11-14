package forge.ai;

import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardLists;
import forge.game.phase.PhaseHandler;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;

/**
 * magic-tools: rule 56. A one-shot pump that also draws a card (Shadow Rift, Impolite Entrance,
 * Expedite...) is first a cantrip: PumpAi only casts it when the pump helps a combat, so without an
 * attacker worth pumping it rots in hand (Poudlar, v8 tournament: Shadow Rift still in hand in 78 % of
 * its losses, 4 copies cast 43 times in 150 games, and every cast also feeds Kiln Fiend and prowess).
 * When the mana would go unused anyway (end of an opponent's turn for an instant, our main 2 for a
 * sorcery), cast it for the card: on our best creature, or at the end of an opponent's turn on any
 * creature, where an "until end of turn" pump no longer does anything.
 */
public final class CantripPumpAi {
    private CantripPumpAi() { }

    public static boolean isCantripPump(final SpellAbility sa) {
        if (sa == null || !sa.isSpell() || sa.getApi() != ApiType.Pump || !sa.usesTargeting() || sa.isCurse()) {
            return false;
        }
        if (sa.hasParam("AILogic") || sa.hasParam("AtEOT") || sa.hasParam("Duration") || sa.hasParam("Defined")
                || !sa.getPayCosts().isOnlyManaCost()) {
            return false;
        }
        if (sa.getTargetRestrictions().getMaxTargets(sa.getHostCard(), sa) != 1
                || !sa.getParamOrDefault("ValidTgts", "").startsWith("Creature")) {
            return false;
        }
        boolean draws = false;
        for (SpellAbility sub = sa.getSubAbility(); sub != null; sub = sub.getSubAbility()) {
            if (sub.getApi() == ApiType.Draw && !sub.usesTargeting()
                    && "You".equals(sub.getParamOrDefault("Defined", "You"))) {
                draws = true;
            } else if (sub.getApi() != ApiType.Scry) {
                return false;
            }
        }
        return draws;
    }

    /** The end of an opponent's turn for an instant, our main 2 for a sorcery, nothing on the stack. */
    public static boolean goodTime(final Player ai, final SpellAbility sa, final PhaseHandler ph) {
        if (!ai.getGame().getStack().isEmpty()) {
            return false;
        }
        if (sa.getHostCard().isInstant()) {
            return ph.is(PhaseType.END_OF_TURN) && ph.getPlayerTurn().isOpponentOf(ai);
        }
        return ph.is(PhaseType.MAIN2) && ph.isPlayerTurn(ai);
    }

    public static boolean chooseTarget(final Player ai, final SpellAbility sa, final PhaseHandler ph) {
        sa.resetTargets();
        CardCollection ours = CardLists.getTargetableCards(ai.getCreaturesInPlay(), sa);
        if (!ours.isEmpty()) {
            sa.getTargets().add(ComputerUtilCard.getBestCreatureAI(ours));
            return true;
        }
        if (!ph.is(PhaseType.END_OF_TURN)) {
            return false;
        }
        CardCollection theirs = new CardCollection();
        for (Player opp : ai.getOpponents()) {
            theirs.addAll(CardLists.getTargetableCards(opp.getCreaturesInPlay(), sa));
        }
        if (theirs.isEmpty()) {
            return false;
        }
        final Card worst = ComputerUtilCard.getWorstCreatureAI(theirs);
        sa.getTargets().add(worst);
        return true;
    }
}
