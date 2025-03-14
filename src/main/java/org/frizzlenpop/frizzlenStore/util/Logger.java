package org.frizzlenpop.frizzlenStore.util;

import org.bukkit.plugin.Plugin;

import java.util.logging.Level;

/**
 * Utility class for standardized logging across the plugin
 */
public class Logger {
    private static java.util.logging.Logger logger;
    private static String prefix;

    /**
     * Initialize the logger with the plugin instance
     * @param plugin The plugin instance
     */
    public static void init(Plugin plugin) {
        logger = plugin.getLogger();
        prefix = "[FrizzlenStore] ";
    }

    /**
     * Log an info message
     * @param message The message to log
     */
    public static void info(String message) {
        logger.info(prefix + message);
    }

    /**
     * Log a warning message
     * @param message The message to log
     */
    public static void warning(String message) {
        logger.warning(prefix + message);
    }

    /**
     * Log a severe message
     * @param message The message to log
     */
    public static void severe(String message) {
        logger.severe(prefix + message);
    }

    /**
     * Log a debug message (only when debug is enabled)
     * @param message The message to log
     */
    public static void debug(String message) {
        logger.log(Level.INFO, prefix + "[DEBUG] " + message);
    }
} 