package org.frizzlenpop.frizzlenStore.commands.subcommands;

import org.bukkit.command.CommandSender;
import org.frizzlenpop.frizzlenStore.FrizzlenStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Command to reload the plugin configuration
 */
public class ReloadCommand implements SubCommand {
    private final FrizzlenStore plugin;
    
    /**
     * Create a new reload command
     * @param plugin The plugin instance
     */
    public ReloadCommand(FrizzlenStore plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public void execute(CommandSender sender, String[] args) {
        // Check permission
        if (!sender.hasPermission(getPermission())) {
            sender.sendMessage("§cYou don't have permission to use this command.");
            return;
        }
        
        // Reload configuration
        plugin.getConfigManager().loadConfigs();
        
        sender.sendMessage("§6[FrizzlenStore] §aConfiguration reloaded successfully!");
    }
    
    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
    
    @Override
    public String getName() {
        return "reload";
    }
    
    @Override
    public String getDescription() {
        return "Reload the plugin configuration";
    }
    
    @Override
    public String getUsage() {
        return "/frizzlenstore reload";
    }
    
    @Override
    public String getPermission() {
        return "frizzlenstore.admin";
    }
    
    @Override
    public List<String> getAliases() {
        List<String> aliases = new ArrayList<>();
        aliases.add("rl");
        return aliases;
    }
} 