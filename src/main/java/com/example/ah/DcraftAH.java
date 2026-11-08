package com.example.ah;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class DcraftAH extends JavaPlugin implements CommandExecutor, Listener {

    private File ahDosya;
    private YamlConfiguration ahYml;
    private Economy econ = null;
    private Object ppAPI = null;
    
    // Hangi oyuncu hangi eşyayı hangi fiyata satacak? (Geçici hafıza)
    private final Map<UUID, ItemStack> BEKLEYEN_ESYA = new HashMap<>();
    private final Map<UUID, Double> BEKLEYEN_FIYAT = new HashMap<>();

    @Override
    public void onEnable() {
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        ahDosya = new File(getDataFolder(), "pazar.yml");
        if (!ahDosya.exists()) { try { ahDosya.createNewFile(); } catch (IOException e) { e.printStackTrace(); } }
        ahYml = YamlConfiguration.loadConfiguration(ahDosya);

        // Vault Bağlantısı
        if (getServer().getPluginManager().getPlugin("Vault") != null) {
            RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp != null) econ = rsp.getProvider();
        }

        // PlayerPoints Bağlantısı (Reflection)
        Plugin ppPlugin = getServer().getPluginManager().getPlugin("PlayerPoints");
        if (ppPlugin != null && ppPlugin.isEnabled()) {
            try { ppAPI = ppPlugin.getClass().getMethod("getAPI").invoke(ppPlugin); } catch (Exception ignored) {}
        }

        if (getCommand("ah") != null) getCommand("ah").setExecutor(this);
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("Dcraft AH (Auction House) aktif!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;

        if (args.length == 0) {
            pazarMenuAc(p);
            return true;
        }

        if (args[0].equalsIgnoreCase("sell") || args[0].equalsIgnoreCase("sat")) {
            if (args.length < 2) {
                p.sendMessage("§cKullanım: /ah sell <fiyat> (Örn: /ah sell 64)");
                return true;
            }

            ItemStack elindeki = p.getInventory().getItemInMainHand();
            if (elindeki == null || elindeki.getType() == Material.AIR) {
                p.sendMessage("§cElinde satacak bir eşya yok!");
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return true;
            }

            double fiyat;
            try {
                fiyat = Double.parseDouble(args[1]);
                if (fiyat <= 0) throw new NumberFormatException();
            } catch (NumberFormatException ex) {
                p.sendMessage("§cGeçerli bir fiyat gir! (Örn: /ah sell 64)");
                return true;
            }

            // Eşyayı elinden al ve birim seçim menüsüne yolla
            BEKLEYEN_ESYA.put(p.getUniqueId(), elindeki.clone());
            BEKLEYEN_FIYAT.put(p.getUniqueId(), fiyat);
            
            // Elinden sil (Birim seçtikten sonra tamamen silinecek, şimdilik envanterden alıyoruz)
            p.getInventory().setItemInMainHand(new ItemStack(Material.AIR));

            birimSecimMenuAc(p, elindeki, fiyat);
            return true;
        }

        p.sendMessage("§cKullanım: /ah veya /ah sell <fiyat>");
        return true;
    }

    private void birimSecimMenuAc(Player p, ItemStack esya, double fiyat) {
        Inventory inv = Bukkit.createInventory(null, 27, "§8§l» §6§lSATIŞ BİRİMİ §8§l«");
        
        ItemStack cam = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta cm = cam.getItemMeta(); if (cm != null) { cm.setDisplayName(" "); cam.setItemMeta(cm); }
        for (int i = 0; i < 27; i++) inv.setItem(i, cam);

        ItemStack para = new ItemStack(Material.GOLD_INGOT);
        ItemMeta pMeta = para.getItemMeta();
        if (pMeta != null) {
            pMeta.setDisplayName("§6§lPARA ile Sat");
            pMeta.setLore(Arrays.asList("§7Bu eşyayı §a" + fiyat + " Para §7karşılığında sat."));
            pMeta.addEnchant(Enchantment.UNBREAKING, 1, true);
            pMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            para.setItemMeta(pMeta);
        }
        inv.setItem(11, para);

        ItemStack puan = new ItemStack(Material.DIAMOND);
        ItemMeta puMeta = puan.getItemMeta();
        if (puMeta != null) {
            puMeta.setDisplayName("§b§lDCRAFT PUANI ile Sat");
            puMeta.setLore(Arrays.asList("§7Bu eşyayı §a" + fiyat + " Puan §7karşılığında sat."));
            puMeta.addEnchant(Enchantment.UNBREAKING, 1, true);
            puMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            puan.setItemMeta(puMeta);
        }
        inv.setItem(15, puan);

        p.openInventory(inv);
    }

    private void pazarMenuAc(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, "§8§l» §6§lAÇIK ARTIRMA §8§l«");
        
        if (ahYml.contains("pazar")) {
            ConfigurationSection sec = ahYml.getConfigurationSection("pazar");
            int slot = 0;
            for (String id : sec.getKeys(false)) {
                if (slot >= 45) break;
                
                String satan = sec.getString(id + ".satan");
                double fiyat = sec.getDouble(id + ".fiyat");
                String birim = sec.getString(id + ".birim");
                String base64 = sec.getString(id + ".item");
                
                ItemStack item = itemFromBase64(base64);
                if (item != null) {
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null) {
                        List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
                        lore.add("§8§m-----------------------");
                        lore.add("§7Satıcı: §f" + satan);
                        lore.add("§7Fiyat: §a" + fiyat + " " + birim.toUpperCase());
                        lore.add("§8§m-----------------------");
                        lore.add("§aSatın almak için tıkla!");
                        lore.add("§8ID: " + id);
                        meta.setLore(lore);
                        item.setItemMeta(meta);
                    }
                    inv.setItem(slot++, item);
                }
            }
        }
        
        ItemStack cam = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta cm = cam.getItemMeta(); if (cm != null) { cm.setDisplayName(" "); cam.setItemMeta(cm); }
        for (int i = 45; i < 54; i++) inv.setItem(i, cam);

        ItemStack bilgi = new ItemStack(Material.PAPER);
        ItemMeta bMeta = bilgi.getItemMeta();
        if (bMeta != null) {
            bMeta.setDisplayName("§e§lNasıl Eşya Satarım?");
            bMeta.setLore(Arrays.asList("§7Elinde satmak istediğin eşyayı tut ve", "§a/ah sell <fiyat> §7yaz.", "§7Örnek: §f/ah sell 64"));
            bilgi.setItemMeta(bMeta);
        }
        inv.setItem(49, bilgi);

        ItemStack kapat = new ItemStack(Material.BARRIER);
        ItemMeta kMeta = kapat.getItemMeta();
        if (kMeta != null) { kMeta.setDisplayName("§c§lKapat"); kapat.setItemMeta(kMeta); }
        inv.setItem(53, kapat);

        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        String title = e.getView().getTitle();
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null) return;

        // --- BİRİM SEÇİM MENÜSÜ ---
        if (title.equals("§8§l» §6§lSATIŞ BİRİMİ §8§l«")) {
            e.setCancelled(true);
            
            if (clicked.getType() == Material.GOLD_INGOT || clicked.getType() == Material.DIAMOND) {
                String birim = clicked.getType() == Material.GOLD_INGOT ? "para" : "puan";
                ItemStack esya = BEKLEYEN_ESYA.remove(p.getUniqueId());
                Double fiyat = BEKLEYEN_FIYAT.remove(p.getUniqueId());
                
                if (esya == null || fiyat == null) { p.sendMessage("§cBir hata oluştu, eşya kayboldu!"); return; }

                // Pazara Ekle
                String id = String.valueOf(System.currentTimeMillis());
                ahYml.set("pazar." + id + ".satan", p.getName());
                ahYml.set("pazar." + id + ".fiyat", fiyat);
                ahYml.set("pazar." + id + ".birim", birim);
                ahYml.set("pazar." + id + ".item", itemToBase64(esya));
                try { ahYml.save(ahDosya); } catch (IOException ignored) {}

                p.sendMessage("§a§l✔ §aEşya başarıyla pazara eklendi!");
                p.sendMessage("§7Fiyat: §a" + fiyat + " " + birim.toUpperCase());
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                p.closeInventory();
            }
            return;
        }

        // --- ANA PAZAR MENÜSÜ ---
        if (title.equals("§8§l» §6§lAÇIK ARTIRMA §8§l«")) {
            e.setCancelled(true);
            
            if (clicked.getType() == Material.BARRIER) { p.closeInventory(); return; }
            if (clicked.getType() == Material.PAPER || clicked.getType() == Material.BLACK_STAINED_GLASS_PANE) return;
            
            if (clicked.hasItemMeta() && clicked.getItemMeta().getLore() != null) {
                String id = null;
                for (String s : clicked.getItemMeta().getLore()) {
                    if (s.startsWith("§8ID: ")) { id = s.replace("§8ID: ", ""); break; }
                }
                if (id != null) satinAl(p, id);
            }
            return;
        }
    }

    private void satinAl(Player alici, String id) {
        String path = "pazar." + id;
        if (!ahYml.contains(path)) { alici.sendMessage("§cEşya artık yok!"); return; }
        
        String satan = ahYml.getString(path + ".satan");
        double fiyat = ahYml.getDouble(path + ".fiyat");
        String birim = ahYml.getString(path + ".birim");
        ItemStack esya = itemFromBase64(ahYml.getString(path + ".item"));

        if (esya == null) { alici.sendMessage("§cEşya yüklenemedi!"); return; }
        if (alici.getName().equals(satan)) { alici.sendMessage("§cKendi eşyanı satın alamazsın!"); return; }

        // Para/Puan Kontrolü ve Transferi
        if (birim.equals("para")) {
            if (econ == null) { alici.sendMessage("§cVault yok!"); return; }
            if (econ.getBalance(alici) < fiyat) { alici.sendMessage("§cYetersiz Para! Gerekli: §e" + fiyat); return; }
            econ.withdrawPlayer(alici, fiyat);
            econ.depositPlayer(Bukkit.getOfflinePlayer(satan), fiyat);
        } else {
            if (ppAPI == null) { alici.sendMessage("§cPlayerPoints yok!"); return; }
            try {
                int bakiye = (int) ppAPI.getClass().getMethod("look", UUID.class).invoke(ppAPI, alici.getUniqueId());
                if (bakiye < fiyat) { alici.sendMessage("§cYetersiz Puan! Gerekli: §e" + fiyat); return; }
                ppAPI.getClass().getMethod("take", UUID.class, int.class).invoke(ppAPI, alici.getUniqueId(), (int) fiyat);
                ppAPI.getClass().getMethod("give", UUID.class, int.class).invoke(ppAPI, Bukkit.getOfflinePlayer(satan).getUniqueId(), (int) fiyat);
            } catch (Exception ex) { alici.sendMessage("§cPuan hatası!"); return; }
        }

        // Eşyayı alıcıya ver
        HashMap<Integer, ItemStack> kalanlar = alici.getInventory().addItem(esya);
        for (ItemStack kalan : kalanlar.values()) alici.getWorld().dropItemNaturally(alici.getLocation(), kalan);

        // Satıcıya mesaj
        Player saticiP = Bukkit.getPlayer(satan);
        if (saticiP != null) saticiP.sendMessage("§a[AH] §e" + alici.getName() + " §7senin eşyanı §e" + fiyat + " " + birim + " §7karşılığında satın aldı!");

        // Kaydet ve menüyü yenile
        ahYml.set(path, null);
        try { ahYml.save(ahDosya); } catch (IOException ignored) {}
        
        alici.sendMessage("§a§l✔ §aEşyayı başarıyla satın aldın!");
        alici.playSound(alici.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        pazarMenuAc(alici); // Yenile
    }

    // --- BASE64 EŞYA KAYDETME (Enchant vs. kaybolmaması için) ---
    private String itemToBase64(ItemStack item) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);
            dataOutput.writeObject(item);
            dataOutput.close();
            return Base64Coder.encodeLines(outputStream.toByteArray());
        } catch (Exception e) { return null; }
    }

    private ItemStack itemFromBase64(String base64) {
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64Coder.decodeLines(base64));
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
            ItemStack item = (ItemStack) dataInput.readObject();
            dataInput.close();
            return item;
        } catch (Exception e) { return null; }
    }
                          }
