package com.example.ah;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
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
    
    private final Map<UUID, ItemStack> BEKLEYEN_ESYA = new HashMap<>();
    private final Map<UUID, Double> BEKLEYEN_FIYAT = new HashMap<>();
    
    private final Map<UUID, String> ARAMA_FILTRE = new HashMap<>();
    private final Map<UUID, Integer> MEVCUT_SAYFA = new HashMap<>();
    
    private final Map<UUID, Location> ARAMA_TABELA = new HashMap<>();

    private final int ESLYA_LIMIT = 10; 
    private final int SAYFA_BASI_ESYA = 45;

    @Override
    public void onEnable() {
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        ahDosya = new File(getDataFolder(), "pazar.yml");
        if (!ahDosya.exists()) { try { ahDosya.createNewFile(); } catch (IOException e) { e.printStackTrace(); } }
        ahYml = YamlConfiguration.loadConfiguration(ahDosya);

        if (getServer().getPluginManager().getPlugin("Vault") != null) {
            RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp != null) econ = rsp.getProvider();
        }

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
            ARAMA_FILTRE.remove(p.getUniqueId());
            MEVCUT_SAYFA.put(p.getUniqueId(), 1);
            pazarMenuAc(p);
            return true;
        }

        if (args[0].equalsIgnoreCase("sell") || args[0].equalsIgnoreCase("sat")) {
            if (args.length < 2) {
                p.sendMessage("§cKullanım: /ah sell <fiyat> (Örn: /ah sell 64)");
                return true;
            }

            int mevcutEsya = getOyuncuEsyaSayisi(p.getUniqueId().toString());
            if (mevcutEsya >= ESLYA_LIMIT) {
                p.sendMessage("§cPazarda aynı anda en fazla §e" + ESLYA_LIMIT + " §ceşya satabilirsin!");
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
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

            BEKLEYEN_ESYA.put(p.getUniqueId(), elindeki.clone());
            BEKLEYEN_FIYAT.put(p.getUniqueId(), fiyat);
            
            p.getInventory().setItemInMainHand(new ItemStack(Material.AIR));

            birimSecimMenuAc(p, elindeki, fiyat);
            return true;
        }

        p.sendMessage("§cKullanım: /ah veya /ah sell <fiyat>");
        return true;
    }

    private int getOyuncuEsyaSayisi(String uuid) {
        int sayi = 0;
        if (ahYml.contains("pazar")) {
            ConfigurationSection sec = ahYml.getConfigurationSection("pazar");
            for (String id : sec.getKeys(false)) {
                if (uuid.equals(sec.getString(id + ".satan_uuid"))) sayi++;
            }
        }
        return sayi;
    }

    private void birimSecimMenuAc(Player p, ItemStack esya, double fiyat) {
        Inventory inv = Bukkit.createInventory(null, 27, "§8§l» §6§lSATIŞ BİRİMİ §8§l«");
        
        ItemStack cam = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta cm = cam.getItemMeta(); if (cm != null) { cm.setDisplayName(" "); cam.setItemMeta(cm); }
        for (int i = 0; i < 27; i++) inv.setItem(i, cam);

        ItemStack ortaEsya = esya.clone();
        ItemMeta eMeta = ortaEsya.getItemMeta();
        if (eMeta != null) {
            List<String> lore = eMeta.hasLore() ? eMeta.getLore() : new ArrayList<>();
            lore.add("");
            lore.add("§aSattığın Eşya");
            eMeta.setLore(lore);
            ortaEsya.setItemMeta(eMeta);
        }
        inv.setItem(13, ortaEsya);

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
        String aranan = ARAMA_FILTRE.get(p.getUniqueId());
        int sayfa = MEVCUT_SAYFA.getOrDefault(p.getUniqueId(), 1);
        
        String title = aranan != null ? "§8§l» §6§lAH (Arama: " + aranan + ") §8§l«" : "§8§l» §6§lAÇIK ARTIRMA §8§l«";
        Inventory inv = Bukkit.createInventory(null, 54, title);
        
        List<String> gecerliIdler = new ArrayList<>();
        
        if (ahYml.contains("pazar")) {
            ConfigurationSection sec = ahYml.getConfigurationSection("pazar");
            for (String id : sec.getKeys(false)) {
                String base64 = sec.getString(id + ".item");
                ItemStack item = itemFromBase64(base64);
                if (item != null) {
                    if (aranan != null && !item.getType().name().contains(aranan.toUpperCase())) continue;
                    gecerliIdler.add(id);
                }
            }
        }
        
        int toplamEsya = gecerliIdler.size();
        int baslangicIndex = (sayfa - 1) * SAYFA_BASI_ESYA;
        int bitisIndex = Math.min(baslangicIndex + SAYFA_BASI_ESYA, toplamEsya);

        int slot = 0;
        for (int i = baslangicIndex; i < bitisIndex; i++) {
            String id = gecerliIdler.get(i);
            String satan = ahYml.getString("pazar." + id + ".satan");
            double fiyat = ahYml.getDouble("pazar." + id + ".fiyat");
            String birim = ahYml.getString("pazar." + id + ".birim");
            ItemStack item = itemFromBase64(ahYml.getString("pazar." + id + ".item"));
            
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
                lore.add("§8§m-----------------------");
                lore.add("§7Satıcı: §f" + satan);
                
                String birimYazi = birim.equals("para") ? "§ePARA" : "§bDCRAFT PUANI";
                lore.add("§7Fiyat: §a" + fiyat + " " + birimYazi);
                
                lore.add("§8§m-----------------------");
                lore.add("§aSatın almak için tıkla!");
                lore.add("§8ID: " + id);
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            inv.setItem(slot++, item);
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

        ItemStack ara = new ItemStack(Material.COMPASS);
        ItemMeta aMeta = ara.getItemMeta();
        if (aMeta != null) {
            aMeta.setDisplayName("§b§lTabela ile Eşya Ara");
            aMeta.setLore(Arrays.asList("§7Pazarda arama yapmak için tıkla.", "§7Karşına bir tabela çıkacak.", "§7Örn: elmas, demir, taş"));
            ara.setItemMeta(aMeta);
        }
        inv.setItem(47, ara);

        if (aranan != null) {
            ItemStack temizle = new ItemStack(Material.BARRIER);
            ItemMeta tMeta = temizle.getItemMeta();
            if (tMeta != null) {
                tMeta.setDisplayName("§c§lAramayı Temizle");
                tMeta.setLore(Arrays.asList("§7Tüm eşyaları tekrar göster."));
                temizle.setItemMeta(tMeta);
            }
            inv.setItem(51, temizle);
        }

        // --- HER ZAMAN GÖRÜNEN SAYFA OKLARI ---
        ItemStack geri = new ItemStack(Material.ARROW);
        ItemMeta gMeta = geri.getItemMeta();
        if (gMeta != null) { 
            if (sayfa > 1) {
                gMeta.setDisplayName("§a§l< Önceki Sayfa"); 
                gMeta.setLore(Arrays.asList("§7Sayfa: " + (sayfa - 1)));
            } else {
                gMeta.setDisplayName("§c§l< Önceki Sayfa"); 
                gMeta.setLore(Arrays.asList("§7Zaten ilk sayfadasın."));
            }
            geri.setItemMeta(gMeta); 
        }
        inv.setItem(48, geri);

        ItemStack ileri = new ItemStack(Material.ARROW);
        ItemMeta iMeta = ileri.getItemMeta();
        if (iMeta != null) { 
            if (bitisIndex < toplamEsya) {
                iMeta.setDisplayName("§a§lSonraki Sayfa >"); 
                iMeta.setLore(Arrays.asList("§7Sayfa: " + (sayfa + 1)));
            } else {
                iMeta.setDisplayName("§c§lSonraki Sayfa >"); 
                iMeta.setLore(Arrays.asList("§7Başka eşya yok."));
            }
            ileri.setItemMeta(iMeta); 
        }
        inv.setItem(50, ileri);

        ItemStack kapat = new ItemStack(Material.BARRIER);
        ItemMeta kMeta = kapat.getItemMeta();
        if (kMeta != null) { kMeta.setDisplayName("§c§lKapat"); kapat.setItemMeta(kMeta); }
        inv.setItem(53, kapat);

        p.openInventory(inv);
    }

    private void onayMenuAc(Player p, String id) {
        String path = "pazar." + id;
        if (!ahYml.contains(path)) { p.sendMessage("§cEşya artık yok!"); pazarMenuAc(p); return; }
        
        String satan = ahYml.getString(path + ".satan");
        double fiyat = ahYml.getDouble(path + ".fiyat");
        String birim = ahYml.getString(path + ".birim");
        ItemStack esya = itemFromBase64(ahYml.getString(path + ".item"));

        if (esya == null) { p.sendMessage("§cEşya yüklenemedi!"); return; }

        Inventory inv = Bukkit.createInventory(null, 27, "§8§l» §6§lSATIN ALMA ONAYI §8§l«");
        
        ItemStack cam = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta cm = cam.getItemMeta(); if (cm != null) { cm.setDisplayName(" "); cam.setItemMeta(cm); }
        for (int i = 0; i < 27; i++) inv.setItem(i, cam);

        ItemMeta eMeta = esya.getItemMeta();
        if (eMeta != null) {
            List<String> lore = eMeta.hasLore() ? eMeta.getLore() : new ArrayList<>();
            lore.add("");
            lore.add("§7Satıcı: §f" + satan);
            String birimYazi = birim.equals("para") ? "§ePARA" : "§bDCRAFT PUANI";
            lore.add("§7Ödenecek: §a" + fiyat + " " + birimYazi);
            eMeta.setLore(lore);
            esya.setItemMeta(eMeta);
        }
        inv.setItem(13, esya);

        ItemStack onay = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta oMeta = onay.getItemMeta();
        if (oMeta != null) {
            oMeta.setDisplayName("§a§lSATIN ALIMI ONAYLA");
            oMeta.setLore(Arrays.asList("§7Bu eşyayı satın almak için tıkla.", "§8ID: " + id));
            oMeta.addEnchant(Enchantment.UNBREAKING, 1, true);
            oMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            onay.setItemMeta(oMeta);
        }
        inv.setItem(11, onay);

        ItemStack iptal = new ItemStack(Material.BARRIER);
        ItemMeta iMeta = iptal.getItemMeta();
        if (iMeta != null) {
            iMeta.setDisplayName("§c§lİPTAL ET");
            iMeta.setLore(Arrays.asList("§7Menüye geri dön."));
            iptal.setItemMeta(iMeta);
        }
        inv.setItem(15, iptal);

        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        String title = e.getView().getTitle();
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null) return;

        if (title.equals("§8§l» §6§lSATIŞ BİRİMİ §8§l«")) {
            e.setCancelled(true);
            
            if (clicked.getType() == Material.GOLD_INGOT || clicked.getType() == Material.DIAMOND) {
                String birim = clicked.getType() == Material.GOLD_INGOT ? "para" : "puan";
                ItemStack esya = BEKLEYEN_ESYA.remove(p.getUniqueId());
                Double fiyat = BEKLEYEN_FIYAT.remove(p.getUniqueId());
                
                if (esya == null || fiyat == null) { p.sendMessage("§cBir hata oluştu, eşya kayboldu!"); return; }

                String id = String.valueOf(System.currentTimeMillis());
                ahYml.set("pazar." + id + ".satan", p.getName());
                ahYml.set("pazar." + id + ".satan_uuid", p.getUniqueId().toString());
                ahYml.set("pazar." + id + ".fiyat", fiyat);
                ahYml.set("pazar." + id + ".birim", birim);
                ahYml.set("pazar." + id + ".item", itemToBase64(esya));
                try { ahYml.save(ahDosya); } catch (IOException ignored) {}

                p.sendMessage("§a§l✔ §aEşya başarıyla pazara eklendi!");
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                p.closeInventory();
            }
            return;
        }

        if (title.equals("§8§l» §6§lSATIN ALMA ONAYI §8§l«")) {
            e.setCancelled(true);
            
            if (clicked.getType() == Material.BARRIER) {
                pazarMenuAc(p); 
                return;
            }
            
            if (clicked.getType() == Material.LIME_STAINED_GLASS_PANE) {
                String id = null;
                if (clicked.getItemMeta().getLore() != null) {
                    for (String s : clicked.getItemMeta().getLore()) {
                        if (s.startsWith("§8ID: ")) { id = s.replace("§8ID: ", ""); break; }
                    }
                }
                if (id != null) satinAl(p, id);
            }
            return;
        }

        if (title.contains("§8§l» §6§lAÇIK ARTIRMA") || title.contains("§8§l» §6§lAH (Arama:")) {
            e.setCancelled(true);
            
            if (clicked.getType() == Material.BARRIER) { 
                if (title.contains("Arama:")) {
                    ARAMA_FILTRE.remove(p.getUniqueId());
                    pazarMenuAc(p);
                } else {
                    p.closeInventory(); 
                }
                return; 
            }
            if (clicked.getType() == Material.PAPER || clicked.getType() == Material.BLACK_STAINED_GLASS_PANE) return;

            if (clicked.getType() == Material.ARROW) {
                int mevcut = MEVCUT_SAYFA.getOrDefault(p.getUniqueId(), 1);
                if (clicked.getItemMeta().getDisplayName().contains("Önceki")) {
                    if (mevcut > 1) {
                        MEVCUT_SAYFA.put(p.getUniqueId(), mevcut - 1);
                        pazarMenuAc(p);
                    }
                } else {
                    // Sağ ok kontrolü (eşya varsa ilerle)
                    MEVCUT_SAYFA.put(p.getUniqueId(), mevcut + 1);
                    pazarMenuAc(p); // Zaten eşya yoksa aynı sayfada kalacak
                }
                return;
            }

            if (clicked.getType() == Material.COMPASS) {
                p.closeInventory();
                tabelaAc(p);
                return;
            }

            if (clicked.hasItemMeta() && clicked.getItemMeta().getLore() != null) {
                String id = null;
                for (String s : clicked.getItemMeta().getLore()) {
                    if (s.startsWith("§8ID: ")) { id = s.replace("§8ID: ", ""); break; }
                }
                if (id != null) onayMenuAc(p, id);
            }
            return;
        }
    }

    private void tabelaAc(Player p) {
        p.closeInventory();
        Location loc = p.getLocation();
        Block block = loc.getWorld().getBlockAt(loc.getBlockX(), loc.getBlockY() - 2, loc.getBlockZ());
        block.setType(Material.OAK_SIGN);
        
        ARAMA_TABELA.put(p.getUniqueId(), block.getLocation());
        
        Sign sign = (Sign) block.getState();
        p.openSign(sign);
        p.sendMessage("§aTabelaya aramak istediğin eşyayı yaz (Örn: elmas, demir) ve Enter'a bas!");
    }

    @EventHandler
    public void onSignChange(SignChangeEvent e) {
        Player p = e.getPlayer();
        if (!ARAMA_TABELA.containsKey(p.getUniqueId())) return;

        String yazilan = e.getLine(0);
        e.setCancelled(true);

        Location tabelaLoc = ARAMA_TABELA.remove(p.getUniqueId());
        if (tabelaLoc != null) tabelaLoc.getBlock().setType(Material.AIR); 

        if (yazilan == null || yazilan.isEmpty()) {
            p.sendMessage("§cBir şey yazmadın, arama iptal edildi!");
            Bukkit.getScheduler().runTask(this, () -> pazarMenuAc(p));
            return;
        }

        String aranan = yazilan.toUpperCase().replace("İ", "I").replace("Ş", "S").replace("Ğ", "G").replace("Ü", "U").replace("Ö", "O").replace("Ç", "C");
        
        if (aranan.contains("ELMAS")) aranan = "DIAMOND";
        else if (aranan.contains("DEMIR")) aranan = "IRON";
        else if (aranan.contains("ALTIN")) aranan = "GOLD";
        else if (aranan.contains("ZUMRUT")) aranan = "EMERALD";
        else if (aranan.contains("KOMUR")) aranan = "COAL";
        else if (aranan.contains("ODUN")) aranan = "LOG";
        else if (aranan.contains("TAS")) aranan = "STONE";
        else if (aranan.contains("TOPRAK")) aranan = "DIRT";
        else if (aranan.contains("KILIC")) aranan = "SWORD";
        else if (aranan.contains("ZIRH")) aranan = "CHESTPLATE";
        else if (aranan.contains("BALTA")) aranan = "AXE";
        else if (aranan.contains("KAZMA")) aranan = "PICKAXE";
        else if (aranan.contains("EKMEK")) aranan = "BREAD";
        else if (aranan.contains("ET")) aranan = "BEEF";

        ARAMA_FILTRE.put(p.getUniqueId(), aranan);
        MEVCUT_SAYFA.put(p.getUniqueId(), 1);
        p.sendMessage("§aEşya aranıyor: §e" + yazilan + " §7(Sistemde: " + aranan + ")");
        
        Bukkit.getScheduler().runTask(this, () -> pazarMenuAc(p));
    }

    private void satinAl(Player alici, String id) {
        String path = "pazar." + id;
        if (!ahYml.contains(path)) { alici.sendMessage("§cEşya artık yok!"); alici.closeInventory(); return; }
        
        String satan = ahYml.getString(path + ".satan");
        String satanUUID = ahYml.getString(path + ".satan_uuid");
        double fiyat = ahYml.getDouble(path + ".fiyat");
        String birim = ahYml.getString(path + ".birim");
        ItemStack esya = itemFromBase64(ahYml.getString(path + ".item"));

        if (esya == null) { alici.sendMessage("§cEşya yüklenemedi!"); return; }
        
        if (satanUUID != null && alici.getUniqueId().toString().equals(satanUUID)) {
            alici.sendMessage("§cKendi eşyanı satın alamazsın!"); 
            alici.closeInventory();
            return; 
        } else if (alici.getName().equals(satan)) {
            alici.sendMessage("§cKendi eşyanı satın alamazsın!"); 
            alici.closeInventory();
            return;
        }

        if (birim.equals("para")) {
            if (econ == null) { alici.sendMessage("§cVault yok!"); return; }
            if (econ.getBalance(alici) < fiyat) { alici.sendMessage("§cYetersiz Para! Gerekli: §e" + fiyat); alici.closeInventory(); return; }
            econ.withdrawPlayer(alici, fiyat);
            econ.depositPlayer(Bukkit.getOfflinePlayer(UUID.fromString(satanUUID)), fiyat);
        } else {
            if (ppAPI == null) { alici.sendMessage("§cPlayerPoints yok!"); return; }
            try {
                int bakiye = (int) ppAPI.getClass().getMethod("look", UUID.class).invoke(ppAPI, alici.getUniqueId());
                if (bakiye < fiyat) { alici.sendMessage("§cYetersiz Puan! Gerekli: §e" + fiyat); alici.closeInventory(); return; }
                ppAPI.getClass().getMethod("take", UUID.class, int.class).invoke(ppAPI, alici.getUniqueId(), (int) fiyat);
                ppAPI.getClass().getMethod("give", UUID.class, int.class).invoke(ppAPI, UUID.fromString(satanUUID), (int) fiyat);
            } catch (Exception ex) { alici.sendMessage("§cPuan hatası!"); return; }
        }

        HashMap<Integer, ItemStack> kalanlar = alici.getInventory().addItem(esya);
        for (ItemStack kalan : kalanlar.values()) alici.getWorld().dropItemNaturally(alici.getLocation(), kalan);

        Player saticiP = Bukkit.getPlayer(UUID.fromString(satanUUID));
        if (saticiP != null) saticiP.sendMessage("§a[AH] §e" + alici.getName() + " §7senin eşyanı §e" + fiyat + " " + birim + " §7karşılığında satın aldı!");

        ahYml.set(path, null);
        try { ahYml.save(ahDosya); } catch (IOException ignored) {}
        
        alici.sendMessage("§a§l✔ §aEşyayı başarıyla satın aldın!");
        alici.playSound(alici.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        
        alici.closeInventory();
        Bukkit.getScheduler().runTaskLater(this, () -> pazarMenuAc(alici), 5L);
    }

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
