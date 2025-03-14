package org.frizzlenpop.frizzlenStore.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.frizzlenpop.frizzlenStore.FrizzlenStore;
import org.frizzlenpop.frizzlenStore.commands.subcommands.HelpCommand;
import org.frizzlenpop.frizzlenStore.commands.subcommands.ReloadCommand;
import org.frizzlenpop.frizzlenStore.commands.subcommands.StoreCommand;
import org.frizzlenpop.frizzlenStore.commands.subcommands.SubCommand;
import org.frizzlenpop.frizzlenStore.util.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages plugin commands
 */
public class CommandManager implements CommandExecutor, TabCompleter {
    private final FrizzlenStore plugin;
    private final Map<String, SubCommand> commands;
    
    /**
     * Create a new command manager
     * @param plugin The plugin instance
     */
    public CommandManager(FrizzlenStore plugin) {
        this.plugin = plugin;
        this.commands = new HashMap<>();
    }
    
    /**
     * Register all commands
     */
    public void registerCommands() {
        // Register main command
        plugin.getCommand("frizzlenstore").setExecutor(this);
        plugin.getCommand("frizzlenstore").setTabCompleter(this);
        
        // Register subcommands
        registerSubCommand(new HelpCommand(plugin));
        registerSubCommand(new ReloadCommand(plugin));
        registerSubCommand(new StoreCommand(plugin));
        
        Logger.info("Registered " + commands.size() + " commands");
    }
    
    /**
     * Register a subcommand
     * @param command The subcommand to register
     */
    private void registerSubCommand(SubCommand command) {
        commands.put(command.getName().toLowerCase(), command);
        
        // Register aliases
        for (String alias : command.getAliases()) {
            commands.put(alias.toLowerCase(), command);
        }
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            // No subcommand specified, show help
            commands.get("help").execute(sender, args);
            return true;
        }
        
        String subCommandName = args[0].toLowerCase();
        
        if (commands.containsKey(subCommandName)) {
            SubCommand subCommand = commands.get(subCommandName);
            
            // Check permission
            if (subCommand.getPermission() != null && !sender.hasPermission(subCommand.getPermission())) {
                sender.sendMessage("§cYou don't have permission to use this command.");
                return true;
            }
            
            // Execute command
            String[] subArgs = new String[args.length - 1];
            System.arraycopy(args, 1, subArgs, 0, args.length - 1);
            
            subCommand.execute(sender, subArgs);
        } else {
            sender.sendMessage("§cUnknown command. Type §7/frizzlenstore help §cfor help.");
        }
        
        return true;
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            // Complete subcommand names
            String partialName = args[0].toLowerCase();
            
            for (SubCommand subCommand : getUniqueCommands()) {
                if (subCommand.getName().toLowerCase().startsWith(partialName)) {
                    if (subCommand.getPermission() == null || sender.hasPermission(subCommand.getPermission())) {
                        completions.add(subCommand.getName());
                    }
                }
            }
        } else if (args.length > 1) {
            // Complete subcommand arguments
            String subCommandName = args[0].toLowerCase();
            
            if (commands.containsKey(subCommandName)) {
                SubCommand subCommand = commands.get(subCommandName);
                
                if (subCommand.getPermission() == null || sender.hasPermission(subCommand.getPermission())) {
                    String[] subArgs = new String[args.length - 1];
                    System.arraycopy(args, 1, subArgs, 0, args.length - 1);
                    
                    completions = subCommand.tabComplete(sender, subArgs);
                }
            }
        }
        
        return completions;
    }
    
    /**
     * Get all unique subcommands (excluding aliases)
     * @return List of unique subcommands
     */
    private List<SubCommand> getUniqueCommands() {
        List<SubCommand> uniqueCommands = new ArrayList<>();
        
        for (SubCommand command : commands.values()) {
            if (!uniqueCommands.contains(command)) {
                uniqueCommands.add(command);
            }
        }
        
        return uniqueCommands;
    }
    
    /**
     * Get a subcommand by name
     * @param name The subcommand name
     * @return The subcommand or null if not found
     */
    public SubCommand getCommand(String name) {
        return commands.getOrDefault(name.toLowerCase(), null);
    }
} 