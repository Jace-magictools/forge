package forge.ai.ability;

import forge.ai.ComputerUtilMana;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseHandler;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 126. A creature whose ETB gives flashback to an instant or sorcery in our graveyard
 * (Snapcaster Mage, AILogic ReplaySpell) is only worth its trigger if some mana is left to replay the spell.
 * Tournament v18, Sorcier: the 2/1 flash was cast during our upkeep, before the land drop, or with one land
 * left: 40 triggers, 13 replays. On our turn it waits for the main phase; it is cast when, once paid, the
 * mana left pays the mana value of some instant or sorcery in our graveyard. Otherwise it is only a body: our
 * second main phase or the end of the opponent's turn.
 */
public final class Replay126 {
    private Replay126() { }

    public static boolean replaysSpell(Card card) {
        for (Trigger t : card.getTriggers()) {
            if (t.getMode() != TriggerType.ChangesZone || !"Battlefield".equals(t.getParam("Destination"))) {
                continue;
            }
            SpellAbility sa = t.ensureAbility();
            if (sa != null && "ReplaySpell".equals(sa.getParam("AILogic"))) {
                return true;
            }
        }
        return false;
    }

    /** NULL: not concerned (no stack-free decision to make). TRUE/FALSE: cast now or not. */
    public static Boolean castNow(Player ai, SpellAbility sa) {
        Card card = sa.getHostCard();
        if (card == null || !card.isInZone(ZoneType.Hand) || !replaysSpell(card)) {
            return null;
        }
        Game game = ai.getGame();
        PhaseHandler ph = game.getPhaseHandler();
        boolean myTurn = ph.isPlayerTurn(ai);
        if (myTurn && ph.getPhase().isBefore(PhaseType.MAIN1)) {
            return Boolean.FALSE;
        }
        int left = ComputerUtilMana.getAvailableManaEstimate(ai, false) - card.getCMC();
        for (Card c : ai.getCardsIn(ZoneType.Graveyard)) {
            if ((c.isInstant() || c.isSorcery()) && c.getCMC() > 0 && c.getCMC() <= left) {
                return null; // usual logic: the replay is affordable
            }
        }
        if (myTurn) {
            return ph.is(PhaseType.MAIN2) ? null : Boolean.FALSE;
        }
        return ph.is(PhaseType.END_OF_TURN) && ph.getNextTurn().equals(ai) ? null : Boolean.FALSE;
    }
}
