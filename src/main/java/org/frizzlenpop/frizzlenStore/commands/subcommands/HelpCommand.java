package org.frizzlenpop.frizzlenStore.commands.subcommands;

import org.bukkit.command.CommandSender;
import org.frizzlenpop.frizzlenStore.FrizzlenStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Command to show help information
 */
public class HelpCommand implements SubCommand {
    private final FrizzlenStore plugin;
    
    /**
     * Create a new help command
     * @param plugin The plugin instance
     */
    public HelpCommand(FrizzlenStore plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public void execute(CommandSender sender, String[] args) {
        sender.sendMessage("§6§l=== FrizzlenStore Help ===");
        sender.sendMessage("§7/frizzlenstore help §f- Show this help message");
        sender.sendMessage("§7/frizzlenstore reload §f- Reload the plugin configuration");
        sender.sendMessage("§7/frizzlenstore store §f- Get the store URL");
        sender.sendMessage("§6§l======================");
    }
    
    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
    
    @Override
    public String getName() {
        return "help";
    }
    
    @Override
    public String getDescription() {
        return "Show help information";
    }
    
    @Override
    public String getUsage() {
        return "/frizzlenstore help";
    }
    
    @Override
    public String getPermission() {
        return null; // No permission required
    }
    
    @Override
    public List<String> getAliases() {
        List<String> aliases = new ArrayList<>();
        aliases.add("?");
        return aliases;
    }
} 