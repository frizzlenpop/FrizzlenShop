package org.frizzlenpop.frizzlenStore.commands.subcommands;

import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * Interface for subcommands
 */
public interface SubCommand {
    
    /**
     * Execute the command
     * @param sender The command sender
     * @param args The command arguments
     */
    void execute(CommandSender sender, String[] args);
    
    /**
     * Get tab completions for the command
     * @param sender The command sender
     * @param args The command arguments
     * @return List of tab completions
     */
    List<String> tabComplete(CommandSender sender, String[] args);
    
    /**
     * Get the command name
     * @return The command name
     */
    String getName();
    
    /**
     * Get the command description
     * @return The command description
     */
    String getDescription();
    
    /**
     * Get the command usage
     * @return The command usage
     */
    String getUsage();
    
    /**
     * Get the command permission
     * @return The command permission
     */
    String getPermission();
    
    /**
     * Get the command aliases
     * @return The command aliases
     */
    List<String> getAliases();
} 