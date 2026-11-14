package forge.ai;

import java.util.List;

import forge.card.MagicColor;
import forge.card.mana.ManaCost;
import forge.game.ability.AbilityUtils;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.phase.PhaseHandler;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.AbilityManaPart;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/**
 * magic-tools: Signets ({1},{T}: add {B}{R}) and filter lands (Darkwater Catacombs). The payment code skips
 * any mana ability with a mana cost (ComputerUtilMana.getAIPlayableMana), so Forge marked them
 * AI:RemoveDeck:All and they sat on the battlefield doing nothing. Here the AI activates one in its own
 * main phase, empty stack, when a spell it wants to cast in hand needs the extra mana or a color only the
 * filter gives; the mana floats in the pool and pays that spell right after.
 */
public final class ManaFilterAi {
    private ManaFilterAi() { }

    public static boolean isFilter(final SpellAbility sa) {
        return sa != null && sa.getApi() == ApiType.Mana && sa.isManaAbility() && sa.getPayCosts() != null
                && sa.getPayCosts().hasManaCost() && sa.getPayCosts().hasTapCost() && !sa.hasParam("AILogic");
    }

    /**
     * magic-tools rule 166: "{T}: add {W}, {G} or {R}, exile the bottom three cards of your library" (Port des Havres
     * Gris, plateau 04/11) is no mana ability under CR 605.1a (it moves cards from a library): it uses the stack and
     * cannot be activated while a spell is being paid, so with four of them the AI never cast a spell costing {R}.
     * Like a filter, it is activated beforehand in our main phase when a spell in hand needs the mana.
     */
    public static boolean isStackSource(final SpellAbility sa) {
        return sa != null && sa.getApi() == ApiType.Mana && sa.getParent() == null && !sa.isManaAbility()
                && !sa.isSpell() && !sa.usesTargeting() && sa.getPayCosts() != null && !sa.getPayCosts().hasManaCost()
                && sa.getPayCosts().hasTapCost() && sa.getHostCard() != null && sa.getHostCard().isPermanent()
                && !sa.hasParam("AILogic") && sa.getParam("Produced") != null && !sa.getParam("Produced").contains("X");
    }

    public static boolean isFilterOrStackSource(final SpellAbility sa) {
        return isFilter(sa) || isStackSource(sa);
    }

    // rule 166: the other untapped permanents of ours with such an ability (one mana each, colors added)
    private static int otherStackSources(final Player ai, final Card host, final byte[] colors) {
        int n = 0;
        for (final Card c : ai.getCardsIn(ZoneType.Battlefield)) {
            if (c == host || c.isTapped()) {
                continue;
            }
            for (final SpellAbility ab : c.getNonManaAbilities()) {
                ab.setActivatingPlayer(ai);
                if (isStackSource(ab) && ab.canPlay()) {
                    n++;
                    colors[0] |= producedColors(ab);
                    break;
                }
            }
        }
        return n;
    }

    public static boolean phaseOk(final Player ai, final PhaseHandler ph) {
        return ph.isPlayerTurn(ai) && ph.getPhase().isMain() && ai.getGame().getStack().isEmpty();
    }

    public static boolean shouldActivate(final Player ai, final SpellAbility sa) {
        if (!isFilterOrStackSource(sa) || !phaseOk(ai, ai.getGame().getPhaseHandler())) {
            return false;
        }
        final Card host = sa.getHostCard();
        final int selfCost = sa.getPayCosts().getCostMana() == null ? 0 : sa.getPayCosts().getCostMana().getMana().getCMC();
        // magic-tools: rule 73 (tester 29/10 11:36/11:38, Great Hall of the Citadel): "Combo Any" counted as two
        // kinds of mana times Amount 2 (4 instead of 2), and the host's own {T}: {C} counted as a source although
        // the filter taps it: with Island + the Hall the AI saw 5 mana, tapped both for Faramir (3) and lost them
        final String producedParam = sa.getParam("Produced");
        int produced = producedParam.startsWith("Combo") || "Any".equals(producedParam) ? 1 : producedParam.split(" ").length;
        if (sa.hasParam("Amount")) {
            produced *= AbilityUtils.calculateAmount(host, sa.getParam("Amount"), sa);
        }
        final List<Card> sourceCards = ComputerUtilMana.getAvailableManaSources(ai, true);
        final int sources = sourceCards.size() + ai.getManaPool().totalMana();
        if (sources < selfCost) {
            return false;
        }
        final byte[] stackColors = {0};
        final int others = isStackSource(sa) ? otherStackSources(ai, host, stackColors) : 0;
        final int after = sources - selfCost + produced - (sourceCards.contains(host) ? 1 : 0) + others;
        final AbilityManaPart part = sa.getManaPart();
        // magic-tools: rule 73, colors of the sources the payment code really uses: getProducibleColors counted the
        // filter's own colors (Signet, Great Hall), so a color only the filter gives never looked missing
        byte colorsNow = 0;
        for (final Card src : sourceCards) {
            for (final SpellAbility ma : ComputerUtilMana.getAIPlayableMana(src)) {
                for (final byte c : MagicColor.WUBRG) {
                    if (ma.getManaPart() != null && ma.getManaPart().canProduce(MagicColor.toShortString(c), ma)) {
                        colorsNow |= c;
                    }
                }
            }
        }
        final byte colorsAfter = (byte) (colorsNow | producedColors(sa) | stackColors[0]);

        for (final Card c : ai.getCardsIn(ZoneType.Hand)) {
            if (c.isLand() || c.isInstant()) {
                continue; // instants are kept for the opponent's turn, a pool does not wait for them
            }
            for (final SpellAbility spell : c.getSpells()) {
                spell.setActivatingPlayer(ai);
                // magic-tools: rule 73, mana for legendary spells only (Great Hall) does not help Sphere of the Suns
                if (part != null && !part.meetsManaRestrictions(spell)) {
                    continue;
                }
                final ManaCost cost = spell.getPayCosts().getTotalMana();
                final int cmc = cost.getCMC();
                if (cmc == 0 || cmc > after) {
                    continue;
                }
                final boolean needsAmount = cmc > sources;
                final boolean needsColor = cost.getColorProfile() != 0 && !cost.canBePaidWithAvailable(colorsNow)
                        && cost.canBePaidWithAvailable(colorsAfter);
                // magic-tools: rule 223, a color we have once does not pay {U}{U} (Narset {1}{U}{U}, one blue land and
                // Guildmages' Forum: the color test saw U and the filter was never used, Patrilock t8-t10)
                final boolean needsSecondSymbol = !needsAmount && !needsColor && isFilter(sa)
                        && cost.getColorProfile() != 0 && !ComputerUtilMana.canPayManaCost(spell, ai, 0, false)
                        && symbolsPaidAfter(cost, sa, sourceCards, host, selfCost, produced);
                if (!needsAmount && !needsColor && !needsSecondSymbol) {
                    continue;
                }
                if (cost.getColorProfile() != 0 && !cost.canBePaidWithAvailable(colorsAfter)) {
                    continue;
                }
                // magic-tools (27/10): the no-cost copy below skips the "wait for Main2" of permanents
                // (PermanentAi: WithoutManaCost), so in Main1 a creature looked castable, the filter was
                // tapped, the creature waited for Main2 and the pool emptied in between
                if (c.isPermanent() && ai.getGame().getPhaseHandler().is(PhaseType.MAIN1, ai)
                        && !ComputerUtil.castPermanentInMain1(ai, spell)) {
                    continue;
                }
                final SpellAbility noCost = spell.copyWithNoManaCost();
                if (noCost == null) {
                    continue;
                }
                noCost.setActivatingPlayer(ai);
                if (((PlayerControllerAi) ai.getController()).getAi().canPlaySa(noCost) == AiPlayDecision.WillPlay) {
                    if (part != null && part.isComboMana()) {
                        part.setExpressChoice(comboColorsFor(cost, colorsNow, produced));
                    }
                    return true;
                }
            }
        }
        return false;
    }

    /** magic-tools: rule 223, each colored symbol gets its own mana once the filter is paid for and activated. */
    private static boolean symbolsPaidAfter(final ManaCost cost, final SpellAbility sa, final List<Card> sourceCards,
            final Card host, final int selfCost, final int produced) {
        final List<Byte> symbols = new java.util.ArrayList<>();
        for (final forge.card.mana.ManaCostShard shard : cost) {
            if (!shard.isGeneric() && !shard.isPhyrexian() && shard.getColorMask() != 0) {
                symbols.add(shard.getColorMask());
            }
        }
        byte wanted = 0;
        for (final byte symbol : symbols) {
            wanted |= symbol;
        }
        final List<Byte> units = new java.util.ArrayList<>();
        for (final Card src : sourceCards) {
            if (src == host) {
                continue;
            }
            byte mask = 0;
            for (final SpellAbility ma : ComputerUtilMana.getAIPlayableMana(src)) {
                for (final byte c : MagicColor.WUBRG) {
                    if (ma.getManaPart() != null && ma.getManaPart().canProduce(MagicColor.toShortString(c), ma)) {
                        mask |= c;
                    }
                }
            }
            units.add(mask);
        }
        // the filter's own cost goes to the sources the spell's colors need least, as the payment does
        final byte want = wanted;
        units.sort(java.util.Comparator.comparingInt(m -> Integer.bitCount(m & want)));
        if (units.size() < selfCost) {
            return false;
        }
        final List<Byte> left = new java.util.ArrayList<>(units.subList(selfCost, units.size()));
        final String producedParam = sa.getParam("Produced");
        final String[] tokens = producedParam.split(" ");
        for (int i = 0; i < produced; i++) {
            final boolean anyOf = producedParam.startsWith("Combo") || producedParam.startsWith("Any");
            left.add(anyOf ? producedColors(sa) : MagicColor.fromName(tokens[i % tokens.length]));
        }
        return matchSymbols(symbols, 0, left);
    }

    private static boolean matchSymbols(final List<Byte> symbols, final int i, final List<Byte> left) {
        if (i == symbols.size()) {
            return true;
        }
        for (int u = 0; u < left.size(); u++) {
            final byte mask = left.get(u);
            if ((mask & symbols.get(i)) != 0) {
                left.remove(u);
                final boolean ok = matchSymbols(symbols, i + 1, left);
                left.add(u, mask);
                if (ok) {
                    return true;
                }
            }
        }
        return false;
    }

    /** magic-tools: rule 73, the combo colors go to the spell's colored shards, those no other source gives first
     *  (Pippin {W}{U} with Island + Great Hall: W U, not the hand's most common color twice). */
    private static String comboColorsFor(final ManaCost cost, final byte colorsNow, final int amount) {
        final List<String> missing = new java.util.ArrayList<>();
        final List<String> others = new java.util.ArrayList<>();
        for (final forge.card.mana.ManaCostShard shard : cost) {
            for (final byte c : MagicColor.WUBRG) {
                if ((shard.getColorMask() & c) != 0) {
                    ((colorsNow & c) == 0 ? missing : others).add(MagicColor.toShortString(c));
                    break;
                }
            }
        }
        missing.addAll(others);
        return String.join(" ", missing.subList(0, Math.min(amount, missing.size())));
    }

    private static byte producedColors(final SpellAbility sa) {
        byte res = 0;
        for (final String p : sa.getParam("Produced").split(" ")) {
            // magic-tools: rule 219, "Combo AnyDifferent" (Interplanar Beacon, Guild Globe) gives every color too:
            // fromName gave 0, the missing color never looked filtered, Teferi stayed in hand (Dovin, t13-t31)
            if (p.startsWith("Any")) {
                return MagicColor.ALL_COLORS;
            }
            res |= MagicColor.fromName(p);
        }
        return res;
    }
}
