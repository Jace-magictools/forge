package forge.ai;

import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardLists;
import forge.game.keyword.Keyword;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;

/**
 * magic-tools: Culling Scales (Eldratron, SVar AICullingScales). A sweeper of small permanents: each upkeep it
 * destroys a nonland permanent of lowest mana value, ours too. With our indestructible rocks (Darksteel Ingot,
 * Skyclave Relic, mana value 3 like the Scales) it only hits the opponent (tester, 24/10).
 * Cast it if, once it is in play, a lowest-mana-value candidate belongs to the opponent, or ours are all
 * indestructible while the opponent has a nonland permanent. Forced to hit our side, the trigger picks
 * an indestructible permanent first.
 */
public final class CullingScalesAi {
    private CullingScalesAi() { }

    public static boolean applies(final Card host) {
        return host.hasSVar("AICullingScales") || isCuller(host);
    }

    // rule 77: a permanent whose upkeep trigger destroys a nonland permanent of lowest mana value (script based)
    public static boolean isCuller(final Card c) {
        for (final Trigger t : c.getTriggers()) {
            if (t.getMode() != TriggerType.Phase || !"Upkeep".equals(t.getParam("Phase"))
                    || !"Battlefield".equals(t.getParam("TriggerZones")) || !t.hasParam("Execute")) {
                continue;
            }
            final String exec = c.getSVar(t.getParam("Execute"));
            if (exec.contains("DB$ Destroy") && exec.contains("lowestCMC")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Rule 77 (a tester report 29/10 12:51, Eldratron): with our Culling Scales in play the AI cast Expedition Map,
     * which became the lowest-mana-value permanent and was destroyed at our next upkeep. A nonland permanent spell is
     * not cast when one of our cullers is in play and, once it resolves, it would be a lowest-mana-value candidate
     * with no opponent permanent among the candidates (and it is not indestructible).
     */
    public static boolean wouldBeCulled(final Player ai, final Card source) {
        // rule 167: a permanent with an instant-speed ability is used in response to the trigger (Map, Top), not lost
        if (source.isLand() || source.hasKeyword(Keyword.INDESTRUCTIBLE) || source.isInPlay() || hasEnterValue(source)
                || OwnThreatResponseAi.hasInstantAbility(source, ai.getLandsInPlay().size())
                || !ai.getCardsIn(ZoneType.Battlefield).anyMatch(c -> !c.equals(source) && isCuller(c))) {
            return false;
        }
        final CardCollection perms = CardLists.filter(ai.getGame().getCardsIn(ZoneType.Battlefield),
                c -> !c.isLand() && !c.equals(source));
        int min = source.getCMC();
        for (final Card c : perms) {
            min = Math.min(min, c.getCMC());
        }
        final int lowest = min;
        return source.getCMC() == lowest
                && !perms.anyMatch(c -> c.getCMC() == lowest && c.getController().isOpponentOf(ai));
    }

    /**
     * Rule 101 (relevé des bourdes, tournoi v12, 30/10 : Trinket Mage 35 fois, Trophy Mage 24 fois gardés en main
     * par Eldratron sous ses Culling Scales) : un permanent dont la valeur est son déclencheur d'arrivée (« when
     * CARDNAME enters », ici chercher un artefact) l'a déjà donnée quand la Balance le détruit à notre entretien ;
     * la règle 77 ne le retient plus. Expedition Map, Top (valeur à l'activation) restent retenus.
     */
    static boolean hasEnterValue(final Card source) {
        for (final Trigger t : source.getTriggers()) {
            if (t.getMode() == TriggerType.ChangesZone && "Battlefield".equals(t.getParam("Destination"))
                    && "Card.Self".equals(t.getParam("ValidCard")) && t.hasParam("Execute") && !t.isStatic()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Rule 77 (a tester report 29/10 12:47, Eldratron): Trinket Mage was cast before Culling Scales (same cost) while the
     * Scales would have destroyed an opponent's creature each turn. A culler in hand whose first target would be an
     * opponent's creature gets a priority bonus.
     */
    public static int priority(final Player ai, final SpellAbility sa) {
        final Card host = sa.getHostCard();
        if (host == null || !sa.isSpell() || !host.isInZone(ZoneType.Hand) || !isCuller(host)) {
            return 0;
        }
        final CardCollection perms = CardLists.filter(ai.getGame().getCardsIn(ZoneType.Battlefield), c -> !c.isLand());
        int min = host.getCMC();
        for (final Card c : perms) {
            min = Math.min(min, c.getCMC());
        }
        final int lowest = min;
        if (host.getCMC() == lowest) {
            return 0;
        }
        final CardCollection candidates = CardLists.filter(perms, c -> c.getCMC() == lowest);
        return candidates.anyMatch(c -> c.isCreature() && c.getController().isOpponentOf(ai))
                && !candidates.anyMatch(c -> !c.getController().isOpponentOf(ai)) ? 4 : 0;
    }

    public static boolean worthCasting(final Player ai, final Card host) {
        final CardCollection perms = CardLists.filter(ai.getGame().getCardsIn(ZoneType.Battlefield), c -> !c.isLand());
        int min = host.getCMC();
        for (final Card c : perms) {
            min = Math.min(min, c.getCMC());
        }
        final int lowest = min;
        final CardCollection candidates = CardLists.filter(perms, c -> c.getCMC() == lowest);
        if (host.getCMC() == lowest) {
            candidates.add(host);
        }
        if (candidates.anyMatch(c -> c.getController().isOpponentOf(ai))) {
            return true;
        }
        final boolean oursSafe = candidates.allMatch(c -> c.hasKeyword(Keyword.INDESTRUCTIBLE));
        return oursSafe && perms.anyMatch(c -> c.getController().isOpponentOf(ai));
    }

    /** Mandatory destroy with no good target: an indestructible permanent survives it. */
    public static Card harmlessTarget(final CardCollection list) {
        return list.stream().filter(c -> c.hasKeyword(Keyword.INDESTRUCTIBLE)).findFirst().orElse(null);
    }
}
