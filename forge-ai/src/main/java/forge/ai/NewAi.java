package forge.ai;

import forge.game.player.Player;

/**
 * magic-tools: duel of AIs (tester 09/11 : new AI against the old one, same deck on both sides, sides swapped).
 * FORGE_OLD_AI=Ai(2)- FORGE_OLD_AI_FROM=264 : players whose name contains the text play without the rules numbered
 * 264 and up (the "old AI"), the others with them. Without FORGE_OLD_AI every rule is on.
 */
public final class NewAi {
    private NewAi() { }

    private static final String OLD = System.getenv("FORGE_OLD_AI");
    private static final int FROM = parse(System.getenv("FORGE_OLD_AI_FROM"));

    private static int parse(String s) {
        try {
            return s == null ? Integer.MAX_VALUE : Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    public static boolean on(Player p, int rule) {
        if (OLD == null || OLD.isEmpty() || rule < FROM || p == null || p.getName() == null) {
            return true;
        }
        return !p.getName().contains(OLD);
    }
}
