package org.frizzlenpop.frizzlenStore.commands.subcommands;

import org.bukkit.command.CommandSender;
import org.frizzlenpop.frizzlenStore.FrizzlenStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Command to show the store URL
 */
public class StoreCommand implements SubCommand {
    private final FrizzlenStore plugin;
    
    /**
     * Create a new store command
     * @param plugin The plugin instance
     */
    public StoreCommand(FrizzlenStore plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public void execute(CommandSender sender, String[] args) {
        // Get store URL from config
        String storeUrl = plugin.getConfigManager().getConfig().getString("store.url", "http://localhost:3000");
        
        sender.sendMessage("§6[FrizzlenStore] §aVisit our store at: §e" + storeUrl);
    }
    
    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
    
    @Override
    public String getName() {
        return "store";
    }
    
    @Override
    public String getDescription() {
        return "Get the store URL";
    }
    
    @Override
    public String getUsage() {
        return "/frizzlenstore store";
    }
    
    @Override
    public String getPermission() {
        return null; // No permission required
    }
    
    @Override
    public List<String> getAliases() {
        List<String> aliases = new ArrayList<>();
        aliases.add("shop");
        aliases.add("buy");
        return aliases;
    }
} 