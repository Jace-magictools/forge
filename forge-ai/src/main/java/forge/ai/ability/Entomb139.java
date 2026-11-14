package forge.ai.ability;

import forge.ai.ComputerUtilMana;
import forge.game.ability.AbilityUtils;
import forge.game.ability.ApiType;
import forge.card.MagicColor;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 139. A spell that puts a card from the library into the graveyard (Entomb) with a
 * reanimation spell in hand (Animate Dead, Necromancy, Reanimate, Exhume) and a mana ritual (Dark Ritual):
 * the stock ritual logic ignores instants and reanimation without a target yet, so the AI spent its only
 * black source on Entomb and reanimated two turns later (Profanation v22, MT07). When the two spells fit
 * this turn only with the ritual's surplus, Entomb waits and the ritual is cast first (main 2, where the
 * stock logic casts Entomb).
 */
public final class Entomb139 {
    private Entomb139() { }

    public static boolean isEntomb(SpellAbility sa) {
        return sa != null && sa.getApi() == ApiType.ChangeZone && sa.isSpell()
                && "Library".equals(sa.getParam("Origin")) && "Graveyard".equals(sa.getParam("Destination"));
    }

    static boolean isReanimation(Card c) {
        if ("Reanimate".equals(c.getSVar("AttachAILogic"))) {
            return true;
        }
        for (SpellAbility sa : c.getSpells()) {
            if (sa.getApi() == ApiType.ChangeZone && "Graveyard".equals(sa.getParam("Origin"))
                    && "Battlefield".equals(sa.getParam("Destination"))) {
                return true;
            }
        }
        for (String v : c.getSVars().values()) {
            if (v.contains("ChangeZone") && v.contains("Origin$ Graveyard") && v.contains("Destination$ Battlefield")
                    && v.contains("Creature")) {
                return true;
            }
        }
        return false;
    }

    static int cmc(SpellAbility sa) {
        return sa.getPayCosts().getTotalMana() == null ? 0 : sa.getPayCosts().getTotalMana().getCMC();
    }

    static SpellAbility ritual(Card c) {
        for (SpellAbility sa : c.getSpells()) {
            if (sa.getApi() == ApiType.Mana && sa.getParamOrDefault("AILogic", "").startsWith("ManaRitual")
                    && !"X".equals(sa.getParam("Produced"))) {
                return sa;
            }
        }
        return null;
    }

    static int produced(SpellAbility ritual) {
        int n = ritual.hasParam("Amount") ? AbilityUtils.calculateAmount(ritual.getHostCard(), ritual.getParam("Amount"), ritual) : 1;
        return n * ritual.getParam("Produced").split(" ").length;
    }

    static int available(Player ai) {
        return ComputerUtilMana.getAvailableManaSources(ai, true).size() + ai.getManaPool().totalMana();
    }

    /** Untapped sources (and floating mana) of the ritual's colour. */
    static int availableOf(Player ai, String color) {
        int n = ai.getManaPool().getAmountOfColor(MagicColor.fromName(color));
        for (Card c : ComputerUtilMana.getAvailableManaSources(ai, true)) {
            for (SpellAbility m : c.getManaAbilities()) {
                if (m.canProduce(color)) {
                    n++;
                    break;
                }
            }
        }
        return n;
    }

    static int pips(String cost, String color) {
        int n = 0;
        for (int i = cost.indexOf("{" + color + "}"); i >= 0; i = cost.indexOf("{" + color + "}", i + 1)) {
            n++;
        }
        return n;
    }

    static String cost(SpellAbility sa) {
        return sa.getPayCosts().getTotalMana() == null ? "" : sa.getPayCosts().getTotalMana().toString();
    }

    static Card cheapestReanimation(Player ai, Card except) {
        Card best = null;
        for (Card c : ai.getCardsIn(ZoneType.Hand)) {
            if (c != except && isReanimation(c) && (best == null || c.getCMC() < best.getCMC())) {
                best = c;
            }
        }
        return best;
    }

    /** Entomb (sa) and a reanimation spell fit this turn only with the ritual's surplus (total and colour). */
    static boolean fitsOnlyWith(Player ai, SpellAbility entomb, SpellAbility rit) {
        if (!ai.getGame().getPhaseHandler().isPlayerTurn(ai) || !ai.getGame().getPhaseHandler().is(PhaseType.MAIN2)) {
            return false;
        }
        Card rea = cheapestReanimation(ai, entomb.getHostCard());
        if (rea == null) {
            return false;
        }
        int need = cmc(entomb) + rea.getCMC();
        int n = available(ai);
        int c = cmc(rit);
        int m = produced(rit);
        String color = rit.getParam("Produced");
        boolean oneColor = color.length() == 1 && "WUBRG".contains(color);
        int pipsNeed = oneColor ? pips(cost(entomb), color) + pips(rea.getManaCost().toString(), color) : 0;
        int pipsRit = oneColor ? pips(cost(rit), color) : 0;
        int cs = oneColor ? availableOf(ai, color) : n;
        boolean fitsNow = n >= need && cs >= pipsNeed;
        boolean fitsWith = n >= c && cs >= pipsRit && n - c + m >= need && cs - pipsRit + m >= pipsNeed;
        return !fitsNow && fitsWith;
    }

    /** Entomb waits: a ritual in hand would let it and a reanimation spell both resolve this turn. */
    public static boolean waitForRitual(Player ai, SpellAbility entomb) {
        if (!isEntomb(entomb)) {
            return false;
        }
        for (Card c : ai.getCardsIn(ZoneType.Hand)) {
            SpellAbility rit = c == entomb.getHostCard() ? null : ritual(c);
            if (rit != null && fitsOnlyWith(ai, entomb, rit)) {
                return true;
            }
        }
        return false;
    }

    /** The ritual (sa) is cast for an Entomb + reanimation in hand. */
    public static boolean ritualForEntomb(Player ai, SpellAbility rit) {
        if (rit.getApi() != ApiType.Mana || !rit.isSpell() || "X".equals(rit.getParam("Produced"))) {
            return false;
        }
        for (Card c : ai.getCardsIn(ZoneType.Hand)) {
            if (c == rit.getHostCard()) {
                continue;
            }
            for (SpellAbility e : c.getSpells()) {
                if (isEntomb(e) && fitsOnlyWith(ai, e, rit)) {
                    return true;
                }
            }
        }
        return false;
    }
}
