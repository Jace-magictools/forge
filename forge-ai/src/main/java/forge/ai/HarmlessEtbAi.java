package forge.ai;

import forge.game.ability.AbilityUtils;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CounterEnumType;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/**
 * magic-tools: an enters-the-battlefield trigger the AI would not run right now is no reason to keep
 * the creature in hand when the trigger cannot hurt us. Stock checkETBEffects asked "would you run this
 * trigger now?" and kept Sygg, Wanderwine Wisdom (a temporary "draw on combat damage" for a creature)
 * and Champions of the Shoal ("tap up to one target creature") in hand forever as BadEtbEffects.
 * Harmless: every part of the trigger either may choose no target, or is a beneficial pump/animate
 * that can target our own creatures, or a keyword given to a card in our own graveyard (Snapcaster Mage:
 * its ReplaySpell logic refuses the trigger when no spell there is worth replaying now, and the 2/1 flash
 * stayed in hand), or only acts on what was already targeted.
 * Also harmless: a non-curse effect, protection or untap that may only target our own creatures
 * (Long River Lurker, grenouille: "target creature you control can't be blocked this turn" is an Effect,
 * kept in hand as BadEtbEffects in 8 turns out of 6 games against Giga; it can target itself).
 * Also harmless: a pump of the creature itself that cannot shrink it (Tribute creatures: Fanatic of Xenagos,
 * refused 10 times out of 4 games of Tricolore against Wurm).
 * Also harmless (rule 32): N -1/-1 counters on "target creature you control" when the creature itself
 * survives N counters, since it may always target itself (Exemplar of Strength, Channeler Initiate,
 * Plague Belcher, Baleful Ammit: kept in hand 24 to 39 turns out of 10 games of Hapatra against Elemental).
 * Also harmless (rule 47): a non-curse pump of ourselves as a player (The One Ring, "you gain protection from
 * everything": PumpAi refuses a player pump outside of danger), and exiling a card from a graveyard, chosen on
 * resolution (Cemetery Illuminator, Cemetery Prowler, Rest in Peace: ChangeZoneAi refuses it when our own
 * graveyard is empty); both kept the card in hand as BadEtbEffects in the r4 blunder run.
 * Also harmless (rule 49): what only helps us (a pump of the card itself written without Defined, our own
 * tokens, +1/+1 counters on itself, amass, a reflexive trigger whose effect is harmless, an opponent's choice
 * between harmless effects) and a target that may only be something an opponent controls (exile, destroy,
 * tap, damage, gain control): Viashino Firstblade, Threefold Thunderhulk, Terrapact Intimidator, Grishnakh,
 * 43 BadEtbEffects in the 160 games of the r12 blunder run.
 * Also harmless (rule 50): a follow-up that only acts on what was targeted, its controller, owner or player
 * (White Orchid Phantom, "destroy up to one target nonbasic land; its controller may search for a basic land":
 * kept in hand 9 times in the r13 blunder run because of DefinedPlayer$ TargetedController).
 * Also harmless (rule 51): +1/+1 counters on a target creature we control (Merfolk Skydiver) and a discard,
 * mill or life loss aimed at an opponent only (Acquisitions Expert), kept in hand in the r14 blunder run.
 * Rule 62: the same check for Saga chapters and "as this enters, choose" replacements (Michiko's Reign of Truth,
 * Hollowmurk Siege), and a creature whose mandatory trigger has no legal target anywhere, itself included
 * (Viridian Corrupter with no artifact, Deputy of Detention against lands only): the trigger is removed, the body
 * stays. v9 tournament: 119, 91, 91 and 89 BadEtbEffects refusals.
 */
public final class HarmlessEtbAi {
    private HarmlessEtbAi() { }

    // rule 62: "destroy target artifact" with no artifact on the battlefield; the host, once in play, is not
    // a legal target either (Skinrender alone must put its counters on itself: still refused)
    public static boolean hasNoTargetAtAll(final SpellAbility trigger, final Card host) {
        if (host == null || !host.isCreature() || !trigger.usesTargeting() || trigger.getMinTargets() == 0) {
            return false;
        }
        final String valid = trigger.getParamOrDefault("ValidTgts", "");
        if (valid.isEmpty() || !trigger.getTargetRestrictions().getZone().contains(ZoneType.Battlefield)
                || trigger.getTargetRestrictions().getNumCandidates(trigger) > 0) {
            return false;
        }
        return !host.isValid(valid.split(","), trigger.getActivatingPlayer(), host, trigger);
    }

    // magic-tools: rule 268 (tester 09/11 13:01), "target Spirit gains hexproof until end of turn" (Rattlechains):
    // a non-curse pump that cannot shrink and whose target type the creature itself has can always go on the
    // creature itself once it is in play; PumpAi refuses it outside danger and the 2/1 flash stayed in hand at the
    // opponent's end step (the "Creature" check of isHarmless missed Spirit, Elf, Soldier...)
    public static boolean isSelfTargetablePump(final SpellAbility trigger, final Card host) {
        if (host == null || !host.isCreature() || trigger.getApi() != ApiType.Pump || trigger.isCurse()
                || !trigger.usesTargeting() || trigger.getSubAbility() != null
                || !NewAi.on(trigger.getActivatingPlayer(), 268)) {
            return false;
        }
        final String valid = trigger.getParamOrDefault("ValidTgts", "");
        if (valid.isEmpty() || valid.contains("Opp") || !trigger.getTargetRestrictions().getZone().contains(ZoneType.Battlefield)
                || trigger.getParamOrDefault("NumAtt", "").startsWith("-")
                || trigger.getParamOrDefault("NumDef", "").startsWith("-")) {
            return false;
        }
        return host.isValid(valid.split(","), trigger.getActivatingPlayer(), host, trigger);
    }

    public static boolean isHarmless(final SpellAbility trigger) {
        for (SpellAbility s = trigger; s != null; s = s.getSubAbility()) {
            if (isOnlyGoodForUs(s) || isOptionalFreeCast(s)) {
                continue;
            }
            if (s.usesTargeting()) {
                if (s.getMinTargets() == 0) {
                    continue;
                }
                final ApiType api = s.getApi();
                final String valid = s.getParamOrDefault("ValidTgts", "");
                if ((api == ApiType.Animate || api == ApiType.Pump) && !s.isCurse()
                        && valid.startsWith("Creature") && !valid.contains("OppCtrl")) {
                    continue;
                }
                if ((api == ApiType.Effect || api == ApiType.Protection
                        || api == ApiType.Untap) && !s.isCurse() && isOwnCreaturesOnly(valid)) {
                    continue;
                }
                if (api == ApiType.PutCounter && !s.isCurse() && "P1P1".equals(s.getParam("CounterType"))
                        && isOwnCreaturesOnly(valid)) {
                    continue;
                }
                if (isSelfSurvivableM1M1(s, valid)) {
                    continue;
                }
                if (api == ApiType.Pump && !s.isCurse() && "Graveyard".equals(s.getParam("TgtZone"))
                        && isOwnCardsOnly(valid)) {
                    continue;
                }
                if (isAgainstOpponentOnly(s, valid)) {
                    continue;
                }
                return false;
            }
            if (isSelfBoost(s) || isOwnPlayerBoost(s) || isGraveyardExile(s)) {
                continue;
            }
            if (!actsOnTargetedOnly(s)) {
                return false;
            }
        }
        return true;
    }

    // rule 236: "you may cast target card from your graveyard without paying its mana cost" can always be declined
    // (Torrential Gearhulk, 5/6 flash, 9 refusals and 0 cast with Dissolve there: ReplaySpell sees nothing to replay
    // now; 31 creatures in all, Goblin Dark-Dwellers, Spell Queller...)
    private static boolean isOptionalFreeCast(final SpellAbility s) {
        return s.getApi() == ApiType.Play && !s.isCurse() && "True".equals(s.getParam("Optional"));
    }

    // rule 236: "if you control that creature, draw two cards": nothing happens unless we target our own creature
    // (Dreamdew Entrancer, 3 refusals and 0 cast)
    private static boolean isOwnDrawIfWeChose(final SpellAbility s) {
        return !s.usesTargeting() && "You".equals(s.getParamOrDefault("Defined", "You"))
                && "Targeted".equals(s.getParam("ConditionDefined"));
    }

    // rule 50: what was already targeted, or its controller, owner or player ("its controller may search their
    // library for a basic land": White Orchid Phantom, Ghost Quarter-like ETBs); the target itself was judged above
    private static boolean actsOnTargetedOnly(final SpellAbility s) {
        final String defined = s.getParam("Defined");
        final String definedPlayer = s.getParam("DefinedPlayer");
        if (defined == null && definedPlayer == null) {
            return false;
        }
        return (defined == null || defined.startsWith("Targeted"))
                && (definedPlayer == null || definedPlayer.startsWith("Targeted"));
    }

    // a pump of the creature itself that cannot shrink it (Tribute: Fanatic of Xenagos, "if tribute wasn't
    // paid, it gets +1/+1 and gains haste": PumpAi refuses it outside combat, and the 3/3 trample stayed in hand)
    private static boolean isSelfBoost(final SpellAbility s) {
        return s.getApi() == ApiType.Pump && !s.isCurse() && isSelf(s)
                && !s.getParamOrDefault("NumAtt", "").startsWith("-")
                && !s.getParamOrDefault("NumDef", "").startsWith("-");
    }

    // rule 49: Pump and PutCounter default to the card itself when neither Defined nor a target is given
    private static boolean isSelf(final SpellAbility s) {
        return "Self".equals(s.getParam("Defined")) || (!s.hasParam("Defined") && !s.usesTargeting());
    }

    // rule 49: our own tokens, +1/+1 counters on itself, amass, a reflexive trigger or an opponent's choice
    // whose effects are all harmless
    private static boolean isOnlyGoodForUs(final SpellAbility s) {
        final ApiType api = s.getApi();
        if (api == null || s.isCurse()) {
            return false;
        }
        switch (api) {
            case Draw:
                return isOwnDrawOfNothing(s) || isOwnDrawIfWeChose(s);
            case Amass:
            case Cleanup:
                return !s.usesTargeting();
            case Token:
                return !s.usesTargeting() && "You".equals(s.getParamOrDefault("TokenOwner", "You"));
            case PutCounter:
                return isSelf(s) && "P1P1".equals(s.getParam("CounterType"));
            case AddOrRemoveCounter:
                // rule 60: "put a charge counter on him or remove one from him" (Guile, Sonic Soldier / Immard)
                return isSelf(s) && !"P1P1".equals(s.getParam("CounterType"))
                        && !"M1M1".equals(s.getParam("CounterType"));
            case Charm:
                // rule 60: a modal effect where we pick one mode, one of them harmless
                if (s.hasParam("Chooser") || !"1".equals(s.getParamOrDefault("CharmNum", "1"))) {
                    return false;
                }
                for (SpellAbility choice : s.getAdditionalAbilityList("Choices")) {
                    if (isHarmless(choice)) {
                        return true;
                    }
                }
                return false;
            case PumpAll:
            case ProtectionAll:
            case UntapAll:
                // rule 60: a mass effect on our own permanents only ("legendary creatures you control gain
                // protection": Éowyn, Fearless Knight)
                return !s.usesTargeting() && isOwnCreaturesOnly(s.getParamOrDefault("ValidCards", ""))
                        && !s.getParamOrDefault("NumAtt", "").startsWith("-")
                        && !s.getParamOrDefault("NumDef", "").startsWith("-");
            case ImmediateTrigger:
                return !s.hasAdditionalAbility("Execute") || isHarmless(s.getAdditionalAbility("Execute"));
            case GenericChoice:
                if (s.usesTargeting() && !isOpponentPlayerOnly(s.getParamOrDefault("ValidTgts", ""))) {
                    return false;
                }
                if (s.getAdditionalAbilityList("Choices").isEmpty()) {
                    return false;
                }
                for (SpellAbility choice : s.getAdditionalAbilityList("Choices")) {
                    if (!isHarmless(choice)) {
                        return false;
                    }
                }
                return true;
            default:
                return false;
        }
    }

    // rule 227: "draw a card for each other Dinosaur you control" with no other Dinosaur draws nothing (DrawAi says no,
    // and Earthshaker Dreadmaw, a 6/6 trample, stayed in hand with 11 lands and an empty board: Jurassic - Smeagol)
    private static boolean isOwnDrawOfNothing(final SpellAbility s) {
        return !s.usesTargeting() && "You".equals(s.getParamOrDefault("Defined", "You"))
                && s.hasParam("NumCards")
                && AbilityUtils.calculateAmount(s.getHostCard(), s.getParam("NumCards"), s) <= 0;
    }

    // rule 49: "exile / destroy / tap / gain control of / N damage to target ... an opponent controls";
    // rule 51: discard, mill, life loss of target opponent
    private static boolean isAgainstOpponentOnly(final SpellAbility s, final String valid) {
        switch (s.getApi()) {
            case ChangeZone:
                if (!"Battlefield".equals(s.getParam("Origin")) || "Battlefield".equals(s.getParam("Destination"))) {
                    return false;
                }
                break;
            case Destroy:
            case Discard:
            case Mill:
            case LoseLife:
            case Tap:
            case GainControl:
            case DealDamage:
                break;
            default:
                return false;
        }
        if (valid.isEmpty()) {
            return false;
        }
        for (String v : valid.split(",")) {
            if (!v.contains("OppCtrl") && !isOpponentPlayerOnly(v)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isOpponentPlayerOnly(final String valid) {
        return "Opponent".equals(valid) || "Player.Opponent".equals(valid);
    }

    // rule 47: "you gain protection from everything until your next turn"
    private static boolean isOwnPlayerBoost(final SpellAbility s) {
        return s.getApi() == ApiType.Pump && !s.isCurse() && "You".equals(s.getParam("Defined"))
                && !s.hasParam("NumAtt") && !s.hasParam("NumDef");
    }

    // rule 47: "exile a card from a graveyard", hidden choice made on resolution, no target, no defined card
    private static boolean isGraveyardExile(final SpellAbility s) {
        return s.getApi() == ApiType.ChangeZone && "Graveyard".equals(s.getParam("Origin"))
                && "Exile".equals(s.getParam("Destination")) && !s.usesTargeting() && !s.hasParam("Defined");
    }

    // rule 32: "put N -1/-1 counters on target creature you control", the host survives N counters
    private static boolean isSelfSurvivableM1M1(final SpellAbility s, final String valid) {
        if (s.getApi() != ApiType.PutCounter || !"M1M1".equals(s.getParam("CounterType"))
                || !isOwnCreaturesOnly(valid) || s.getMaxTargets() > 1) {
            return false;
        }
        final Card host = s.getHostCard();
        if (host == null || !host.isCreature() || !host.canReceiveCounters(CounterEnumType.M1M1)) {
            return false;
        }
        final int n;
        try {
            n = Integer.parseInt(s.getParamOrDefault("CounterNum", "1"));
        } catch (NumberFormatException e) {
            return false;
        }
        return host.getNetToughness() > n;
    }

    private static boolean isOwnCreaturesOnly(final String valid) {
        if (valid.isEmpty()) {
            return false;
        }
        for (String v : valid.split(",")) {
            if (!(v.startsWith("Creature") || v.startsWith("Permanent")) || !v.contains(".YouCtrl")) {
                return false;
            }
        }
        return true;
    }

    private static boolean isOwnCardsOnly(final String valid) {
        if (valid.isEmpty()) {
            return false;
        }
        for (String v : valid.split(",")) {
            if (!v.contains(".YouCtrl") && !v.contains(".YouOwn")) {
                return false;
            }
        }
        return true;
    }
}
