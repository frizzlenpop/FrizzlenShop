package org.frizzlenpop.frizzlenStore.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.frizzlenpop.frizzlenStore.FrizzlenStore;
import org.frizzlenpop.frizzlenStore.util.Logger;

/**
 * Listens for player events like joining and quitting
 */
public class PlayerListener implements Listener {
    private final FrizzlenStore plugin;
    
    /**
     * Create a new player listener
     * @param plugin The plugin instance
     */
    public PlayerListener(FrizzlenStore plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Handle player join events
     * @param event The player join event
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Check for pending purchases
        plugin.getPurchaseManager().deliverPendingPurchases(player);
        
        // If player is admin, send notification about recent purchases
        if (player.hasPermission("frizzlenstore.admin")) {
            int pendingCount = plugin.getPurchaseManager().getPendingPurchasesCount();
            if (pendingCount > 0) {
                player.sendMessage("§6[FrizzlenStore] §aThere are §e" + pendingCount + " §apending purchases to process.");
            }
        }
    }
    
    /**
     * Handle player quit events
     * @param event The player quit event
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        
        // Clean up any resources for this player
        // For example, remove from active checkout sessions
        Logger.debug("Player " + player.getName() + " quit, cleaning up resources");
    }
} 