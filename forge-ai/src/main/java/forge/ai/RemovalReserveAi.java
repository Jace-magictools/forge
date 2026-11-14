package forge.ai;

import java.util.Map;

import forge.game.Game;
import forge.game.ability.AbilityKey;
import forge.game.ability.ApiType;
import forge.game.GameEntityCounterTable;
import forge.game.replacement.ReplacementEffect;
import forge.game.replacement.ReplacementLayer;
import forge.game.replacement.ReplacementType;
import forge.game.card.Card;
import forge.game.keyword.Keyword;
import forge.game.phase.PhaseHandler;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.staticability.StaticAbility;
import forge.game.staticability.StaticAbilityMode;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 74 (a tester reports 29/10 12:49 and 12:57). Unconditional removal (destroy, exile, no damage) was
 * spent at random on a creature that threatens nothing, while a creature of our hand could use that mana: Vampire
 * cast Cast Down on Trinket Mage (2/2, its trigger already resolved) in its upkeep and could no longer cast Cordial
 * Vampire; Avacyn cast Oblivion Ring on Nightsky Mimic (2/1) instead of Righteous Valkyrie. Stock useRemovalNow gives
 * a 2/2 a chance of ~0.35 per look, so the removal goes sooner or later.
 * Held: an opponent creature with power 2 or less, worth little (evaluateCreature below 190: a vanilla 2/2 for 3 is 166), with no activated
 * ability (mana abilities included: combo pieces and mana dorks keep their removal), no continuous effect on other
 * cards, not equipped or enchanted, no evasion out of reach of our blockers; while our life is not in danger and a
 * creature of our hand can be cast with the mana that the removal would take. A bigger threat gets the removal later.
 */
public final class RemovalReserveAi {
    private RemovalReserveAi() { }

    public static boolean holdForThreat(final SpellAbility sa, final Card c, final int dmg) {
        final Player ai = sa.getActivatingPlayer();
        if (dmg != 0 || !sa.isSpell() || c == null || !c.isCreature() || !c.getController().isOpponentOf(ai)) {
            return false;
        }
        final Game game = ai.getGame();
        final PhaseHandler ph = game.getPhaseHandler();
        // a blocker of our attacker in combat is another matter (useRemovalNow, interrupt 2)
        if (ph.inCombat() && !ph.isPlayerTurn(ai)) {
            return false;
        }
        if (!harmless(c) || ComputerUtil.aiLifeInDanger(ai, false, 0)) {
            return false;
        }
        return creatureCompetesForMana(ai, sa);
    }

    /**
     * magic-tools: rule 281 (Forge task, 10/11 point 17 h, follows rule 276b). Against a deck whose plan has combos
     * (Combo$), our last removal of its kind is not spent on a harmless creature that is not a combo piece, while our
     * life is not in danger: the combo piece still to come gets it (276b aims the removal at it once in play).
     */
    public static boolean holdForCombo(final SpellAbility sa, final Card c, final int dmg) {
        final Player ai = sa.getActivatingPlayer();
        if (ai == null || !NewAi.on(ai, 281) || dmg != 0 || !sa.isSpell() || c == null || !c.isCreature()
                || !c.getController().isOpponentOf(ai)) {
            return false;
        }
        final Player opp = c.getController();
        if (DeckPlanAi.combos(opp).isEmpty() || DeckPlanAi.isComboPiece(opp, c)) {
            return false;
        }
        final PhaseHandler ph = ai.getGame().getPhaseHandler();
        if (ph.inCombat() && !ph.isPlayerTurn(ai)) {
            return false;
        }
        if (!harmless(c) || ComputerUtil.aiLifeInDanger(ai, false, 0)) {
            return false;
        }
        return !otherRemovalInHand(ai, sa);
    }

    static boolean otherRemovalInHand(final Player ai, final SpellAbility removal) {
        for (final Card h : ai.getCardsIn(ZoneType.Hand)) {
            if (h == removal.getHostCard()) {
                continue;
            }
            for (final SpellAbility s : h.getSpells()) {
                if (s.getApi() != null && s.getApi() == removal.getApi() && s.usesTargeting()) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean harmless(final Card c) {
        if (c.getNetPower() > 2 || ComputerUtilCard.evaluateCreature(c) >= 190 || c.isEquipped() || c.isEnchanted()) {
            return false;
        }
        if (!c.getManaAbilities().isEmpty()) {
            return false;
        }
        for (final SpellAbility a : c.getSpellAbilities()) {
            if (a.isActivatedAbility()) {
                return false;
            }
        }
        for (final StaticAbility st : c.getStaticAbilities()) {
            if (st.checkMode(StaticAbilityMode.Continuous) && st.hasParam("Affected")
                    && !"Card.Self".equals(st.getParam("Affected"))) {
                return false;
            }
        }
        if (c.hasKeyword(Keyword.INFECT) || c.hasKeyword(Keyword.TOXIC) || c.hasKeyword(Keyword.WITHER)) {
            return false;
        }
        return true;
    }

    /**
     * magic-tools: rule 85 (a tester report 29/10 15:15, Gwyll - Dauthi). In its draw step, with one Swamp in play and one
     * in hand, Dauthi cast Cut Down on Tithe Drinker: the land drop then left one mana and Dauthi Horror stayed in hand.
     * Before its main phase on its own turn, the AI counts one more mana when its land drop is still to come and a land
     * of its hand enters untapped with a mana ability.
     */
    static int landDropMana(final Player ai) {
        final PhaseHandler ph = ai.getGame().getPhaseHandler();
        if (!ph.isPlayerTurn(ai) || !ph.getPhase().isBefore(PhaseType.MAIN1)) {
            return 0;
        }
        for (final Card land : ai.getCardsIn(ZoneType.Hand)) {
            if (land.isLand() && !land.getManaAbilities().isEmpty() && ai.canPlayLand(land, true, null)
                    && !entersTapped(ai, land)) {
                return 1;
            }
        }
        return 0;
    }

    static boolean entersTapped(final Player ai, final Card land) {
        final Map<AbilityKey, Object> repParams = AbilityKey.mapFromAffected(land);
        repParams.put(AbilityKey.Origin, land.getZone().getZoneType());
        repParams.put(AbilityKey.Destination, ZoneType.Battlefield);
        final GameEntityCounterTable table = new GameEntityCounterTable();
        repParams.put(AbilityKey.EffectOnly, true);
        repParams.put(AbilityKey.CounterTable, table);
        repParams.put(AbilityKey.CounterMap, table.column(land));
        for (final ReplacementEffect re : ai.getGame().getReplacementHandler().getReplacementList(ReplacementType.Moved,
                repParams, ReplacementLayer.Other)) {
            final SpellAbility reSA = re.ensureAbility();
            if (reSA == null || !ApiType.Tap.equals(reSA.getApi())) {
                continue;
            }
            reSA.setActivatingPlayer(reSA.getHostCard().getController());
            if (reSA.metConditions()) {
                return true;
            }
        }
        return false;
    }

    /** A creature of our hand castable now, that the mana of the removal would keep in hand. */
    static boolean creatureCompetesForMana(final Player ai, final SpellAbility removal) {
        // magic-tools: rule 85, in our upkeep or draw step the land drop still to come pays for the creature too
        final int landDrop = landDropMana(ai);
        final int mana = ComputerUtilMana.getAvailableManaEstimate(ai, false) + landDrop;
        final int removalCost = removal.getPayCosts() != null && removal.getPayCosts().getTotalMana() != null
                ? removal.getPayCosts().getTotalMana().getCMC() : removal.getHostCard().getCMC();
        final boolean sorceryWindow = ai.getGame().getPhaseHandler().isPlayerTurn(ai);
        for (final Card h : ai.getCardsIn(ZoneType.Hand)) {
            if (h == removal.getHostCard() || !h.isCreature()) {
                continue;
            }
            for (final SpellAbility s : h.getSpells()) {
                s.setActivatingPlayer(ai);
                final int cost = s.getPayCosts() != null && s.getPayCosts().getTotalMana() != null
                        ? s.getPayCosts().getTotalMana().getCMC() : h.getCMC();
                if (cost + removalCost <= mana || cost > mana) {
                    continue;
                }
                if (!sorceryWindow && !h.hasKeyword(Keyword.FLASH)) {
                    continue;
                }
                // with the land drop still to come, the cost of the creature is all we can check
                if (landDrop > 0 || ComputerUtilMana.canPayManaCost(s, ai, 0, false)) {
                    return true;
                }
            }
        }
        return false;
    }
}
