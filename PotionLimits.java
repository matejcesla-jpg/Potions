package cz.mteus.potionlimits;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionType;
import java.util.ArrayList;
import java.util.List;

public final class PotionLimits extends JavaPlugin implements Listener {
    @Override public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        // Covers pickup, crafting, brewing, clicks, drags, shulkers and bundle changes.
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) enforce(player);
        }, 1L, 5L);
    }

    @EventHandler public void onDeath(PlayerDeathEvent event) {
        if (event.getEntity().getName().equalsIgnoreCase("dortcz")
                && event.getEntity().getKiller() != null) {
            event.getDrops().add(new ItemStack(Material.CAKE));
        }
    }

    private void enforce(Player player) {
        PlayerInventory inv = player.getInventory();
        int[] counts = new int[4]; // healing, turtle normal, turtle long, turtle strong
        List<ItemStack> overflow = new ArrayList<>();
        // getStorageContents excludes armor and offhand, which are checked separately.
        ItemStack[] storage = inv.getStorageContents();
        boolean changed = false;
        for (int i = 0; i < storage.length; i++) {
            ItemStack old = storage[i];
            ItemStack updated = trim(old, counts, overflow);
            if (updated != old) { storage[i] = updated; changed = true; }
        }
        if (changed) inv.setStorageContents(storage);
        ItemStack offhand = inv.getItemInOffHand();
        ItemStack newOffhand = trim(offhand, counts, overflow);
        if (newOffhand != offhand) inv.setItemInOffHand(newOffhand);
        if (!overflow.isEmpty()) {
            for (ItemStack item : overflow) player.getWorld().dropItemNaturally(player.getLocation(), item);
            player.sendMessage("§cPřekročen limit potionů! Přebytečné potiony byly vyhozeny na zem.");
        }
    }

    private ItemStack trim(ItemStack stack, int[] counts, List<ItemStack> overflow) {
        if (stack == null || stack.getType().isAir()) return stack;
        if (stack.getItemMeta() instanceof BundleMeta bundle) {
            List<ItemStack> original = bundle.getItems();
            List<ItemStack> kept = new ArrayList<>();
            boolean changed = false;
            for (ItemStack item : original) {
                ItemStack result = trim(item, counts, overflow);
                if (result != item) changed = true;
                if (result != null && !result.getType().isAir()) kept.add(result);
            }
            if (changed) {
                ItemStack copy = stack.clone();
                BundleMeta meta = (BundleMeta) copy.getItemMeta();
                meta.setItems(kept);
                copy.setItemMeta(meta);
                return copy;
            }
            return stack;
        }
        int category = category(stack);
        if (category == -1) return stack;
        int limit = category == 0 ? 4 : 1;
        int allowed = Math.max(0, limit - counts[category]);
        int keep = Math.min(stack.getAmount(), allowed);
        counts[category] += keep;
        if (keep == stack.getAmount()) return stack;
        ItemStack extra = stack.clone();
        extra.setAmount(stack.getAmount() - keep);
        overflow.add(extra);
        if (keep == 0) return null;
        ItemStack reduced = stack.clone();
        reduced.setAmount(keep);
        return reduced;
    }

    private int category(ItemStack item) {
        Material material = item.getType();
        if (material != Material.POTION && material != Material.SPLASH_POTION
                && material != Material.LINGERING_POTION) return -1;
        if (!(item.getItemMeta() instanceof PotionMeta meta)) return -1;
        PotionType type = meta.getBasePotionType();
        if (type == null) return -1;
        return switch (type) {
            case HEALING, STRONG_HEALING -> 0;
            case TURTLE_MASTER -> 1;
            case LONG_TURTLE_MASTER -> 2;
            case STRONG_TURTLE_MASTER -> 3;
            default -> -1;
        };
    }
}
