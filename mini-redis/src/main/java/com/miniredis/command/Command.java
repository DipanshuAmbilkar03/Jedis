package com.miniredis.command;

import java.util.List;

/**
 * Represents a parsed Redis command with its arguments.
 * 
 * @param name the command name (uppercase), e.g., "SET", "GET"
 * @param args the full argument list including the command name at index 0
 */
public record Command(String name, List<String> args) {

    /**
     * Create a Command from a raw list of strings.
     * The first element is treated as the command name (uppercased).
     */
    public static Command from(List<String> rawArgs) {
        if (rawArgs == null || rawArgs.isEmpty()) {
            return new Command("", List.of());
        }
        String name = rawArgs.getFirst().toUpperCase();
        return new Command(name, rawArgs);
    }

    /**
     * Get argument at position (0 = command name, 1 = first arg, etc.)
     */
    public String getArg(int index) {
        if (index < 0 || index >= args.size()) {
            return null;
        }
        return args.get(index);
    }

    /**
     * Get the number of arguments (including the command name).
     */
    public int argCount() {
        return args.size();
    }
}
