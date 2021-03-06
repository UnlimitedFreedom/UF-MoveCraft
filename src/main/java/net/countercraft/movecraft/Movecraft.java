/*
 * This file is part of Movecraft.
 *
 *     Movecraft is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Movecraft is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Movecraft.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.countercraft.movecraft;

import net.countercraft.movecraft.async.AsyncManager;
import at.pavlov.cannons.Cannons;
import com.earth2me.essentials.Essentials;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import net.countercraft.movecraft.config.Settings;
import net.countercraft.movecraft.craft.CraftManager;
import net.countercraft.movecraft.items.StorageChestItem;
import net.countercraft.movecraft.listener.BlockListener;
import net.countercraft.movecraft.listener.CommandListener;
import net.countercraft.movecraft.listener.InteractListener;
import net.countercraft.movecraft.listener.PlayerListener;
import net.countercraft.movecraft.listener.WorldEditInteractListener;
import net.countercraft.movecraft.localisation.I18nSupport;
import net.countercraft.movecraft.metrics.MovecraftMetrics;
import net.countercraft.movecraft.utils.MapUpdateManager;
import net.countercraft.movecraft.utils.MovecraftLocation;
import net.countercraft.movecraft.utils.TownyUtils;
import net.countercraft.movecraft.utils.WGCustomFlagsUtils;
import net.milkbowl.vault.economy.Economy;
import com.mewin.WGCustomFlags.WGCustomFlagsPlugin;
import com.palmergames.bukkit.towny.Towny;
import com.sk89q.worldguard.protection.flags.StateFlag;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import java.io.File;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;

public class Movecraft extends JavaPlugin {

    public static Movecraft plugin;
    private static WorldGuardPlugin worldGuardPlugin;
    private static WorldEditPlugin worldEditPlugin;
    private static WGCustomFlagsPlugin wgCustomFlagsPlugin = null;
    private static Economy economy;
    private static Cannons cannonsPlugin = null;
    private static Towny townyPlugin = null;
    private static Essentials essentialsPlugin = null;
    public static StateFlag FLAG_PILOT = null; //new StateFlag("movecraft-pilot", true);
    public static StateFlag FLAG_MOVE = null; //new StateFlag("movecraft-move", true);
    public static StateFlag FLAG_ROTATE = null; //new StateFlag("movecraft-rotate", true);
    public static StateFlag FLAG_SINK = null; //new StateFlag("movecraft-sink", true);        
    @SuppressWarnings("NonConstantLogger")
    private Logger logger;
    private boolean shuttingDown;
    public HashMap<MovecraftLocation, Long> blockFadeTimeMap = new HashMap<MovecraftLocation, Long>();
    public HashMap<MovecraftLocation, Integer> blockFadeTypeMap = new HashMap<MovecraftLocation, Integer>();
    public HashMap<MovecraftLocation, Boolean> blockFadeWaterMap = new HashMap<MovecraftLocation, Boolean>();
    public HashMap<MovecraftLocation, World> blockFadeWorldMap = new HashMap<MovecraftLocation, World>();

    @Override
    public void onDisable() {
        // Process the storage crates to disk
        if (Settings.DisableCrates == false) {
            StorageChestItem.saveToDisk();
        }
        shuttingDown = true;
    }

    @Override
    @SuppressWarnings("ResultOfObjectAllocationIgnored")
    public void onEnable() {
        // Read in config            
        plugin.saveDefaultConfig();
        Settings.LOCALE = plugin.getConfig().getString("Locale");
        Settings.DisableCrates = plugin.getConfig().getBoolean("DisableCrates", false);
        Settings.Debug = plugin.getConfig().getBoolean("Debug", false);
        Settings.DisableSpillProtection = plugin.getConfig().getBoolean("DisableSpillProtection", false);
        // if the PilotTool is specified in the config.yml file, use it
        
        // Suppress logs of the plugin
        
        if (plugin.getConfig().getInt("PilotTool") != 0) {
            logger.log(Level.INFO, "Recognized PilotTool setting of: {0}", plugin.getConfig().getInt("PilotTool"));
            Settings.PilotTool = plugin.getConfig().getInt("PilotTool");
        } else {
            logger.log(Level.INFO, "No PilotTool setting, using default of 280");
        }
        // if the CompatibilityMode is specified in the config.yml file, use it.
        // Otherwise set to false.
        Settings.CompatibilityMode = plugin.getConfig().getBoolean("CompatibilityMode", false);
        if (Settings.CompatibilityMode == false) {
            try {
                Class.forName("net.minecraft.server.v1_8_R3.Chunk");
            } catch (ClassNotFoundException e) {
                Settings.CompatibilityMode = true;
                logger.log(Level.INFO, "WARNING: CompatibilityMode was set to false, but required build-specific classes were not found. FORCING COMPATIBILITY MODE");
            }
        }
        logger.log(Level.INFO, "CompatiblityMode is set to {0}", Settings.CompatibilityMode);
        Settings.SinkRateTicks = plugin.getConfig().getDouble("SinkRateTicks", 20.0);
        Settings.SinkCheckTicks = plugin.getConfig().getDouble("SinkCheckTicks", 100.0);
        Settings.TracerRateTicks = plugin.getConfig().getDouble("TracerRateTicks", 5.0);
        Settings.ManOverBoardTimeout = plugin.getConfig().getInt("ManOverBoardTimeout", 30);
        Settings.FireballLifespan = plugin.getConfig().getInt("FireballLifespan", 6);
        Settings.FireballPenetration = plugin.getConfig().getBoolean("FireballPenetration", true);
        Settings.BlockQueueChunkSize = plugin.getConfig().getInt("BlockQueueChunkSize", 1000);
        Settings.AllowCrewSigns = plugin.getConfig().getBoolean("AllowCrewSigns", true);
        Settings.RequireCreatePerm = plugin.getConfig().getBoolean("RequireCreatePerm", false);
        Settings.TNTContactExplosives = plugin.getConfig().getBoolean("TNTContactExplosives", true);
        Settings.FadeWrecksAfter = plugin.getConfig().getInt("FadeWrecksAfter", 0);
        //load up WorldGuard if it's present
        Plugin wGPlugin = Bukkit.getServer().getPluginManager().getPlugin("WorldGuard");
        if (wGPlugin == null || !(wGPlugin instanceof WorldGuardPlugin)) {
            // logger.log(Level.INFO, "Movecraft did not find a compatible version of WorldGuard. Disabling WorldGuard integration");
        } else {
            logger.log(Level.INFO, "Found a compatible version of WorldGuard. Enabling WorldGuard integration");
            Settings.WorldGuardBlockMoveOnBuildPerm = plugin.getConfig().getBoolean("WorldGuardBlockMoveOnBuildPerm", false);
            Settings.WorldGuardBlockSinkOnPVPPerm = plugin.getConfig().getBoolean("WorldGuardBlockSinkOnPVPPerm", false);
            logger.log(Level.INFO, "Settings: WorldGuardBlockMoveOnBuildPerm - {0}, WorldGuardBlockSinkOnPVPPerm - {1}", new Object[]{Settings.WorldGuardBlockMoveOnBuildPerm, Settings.WorldGuardBlockSinkOnPVPPerm});

        }
        worldGuardPlugin = (WorldGuardPlugin) wGPlugin;

        //load up WorldEdit if it's present
        Plugin wEPlugin = Bukkit.getServer().getPluginManager().getPlugin("WorldEdit");
        if (wEPlugin == null || !(wEPlugin instanceof WorldEditPlugin)) {
            logger.log(Level.SEVERE, "Movecraft did not find a compatible version of WorldEdit! HAVING NO WORLDEDIT IS BAD!");
        } else {
            // logger.log(Level.INFO, "Found a compatible version of WorldEdit. Enabling WorldEdit integration");
            Settings.RepairTicksPerBlock = plugin.getConfig().getInt("RepairTicksPerBlock", 0);
        }
        worldEditPlugin = (WorldEditPlugin) wEPlugin;

        // next is Cannons
        Plugin plug = Bukkit.getServer().getPluginManager().getPlugin("Cannons");
        if (plug != null && plug instanceof Cannons) {
            cannonsPlugin = (Cannons) plug;
            logger.log(Level.INFO, "Found a compatible version of Cannons. Enabling Cannons integration");
        }

        if (worldGuardPlugin != null || worldGuardPlugin instanceof WorldGuardPlugin) {
            if (worldGuardPlugin.isEnabled()) {
                Plugin tempWGCustomFlagsPlugin = getServer().getPluginManager().getPlugin("WGCustomFlags");
                if (tempWGCustomFlagsPlugin != null && tempWGCustomFlagsPlugin instanceof WGCustomFlagsPlugin) {
                    logger.log(Level.INFO, "Found a compatible version of WGCustomFlags. Enabling WGCustomFlags integration.");
                    wgCustomFlagsPlugin = (WGCustomFlagsPlugin) tempWGCustomFlagsPlugin;
                    WGCustomFlagsUtils WGCFU = new WGCustomFlagsUtils();
                    FLAG_PILOT = WGCFU.getNewStateFlag("movecraft-pilot", true);
                    FLAG_MOVE = WGCFU.getNewStateFlag("movecraft-move", true);
                    FLAG_ROTATE = WGCFU.getNewStateFlag("movecraft-rotate", true);
                    FLAG_SINK = WGCFU.getNewStateFlag("movecraft-sink", true);
                    WGCFU.init();
                    Settings.WGCustomFlagsUsePilotFlag = getConfig().getBoolean("WGCustomFlagsUsePilotFlag", false);
                    Settings.WGCustomFlagsUseMoveFlag = getConfig().getBoolean("WGCustomFlagsUseMoveFlag", false);
                    Settings.WGCustomFlagsUseRotateFlag = getConfig().getBoolean("WGCustomFlagsUseRotateFlag", false);
                    Settings.WGCustomFlagsUseSinkFlag = getConfig().getBoolean("WGCustomFlagsUseSinkFlag", false);
                    logger.log(Level.INFO, "Settings: WGCustomFlagsUsePilotFlag - {0}", Settings.WGCustomFlagsUsePilotFlag);
                    logger.log(Level.INFO, "Settings: WGCustomFlagsUseMoveFlag - {0}", Settings.WGCustomFlagsUseMoveFlag);
                    logger.log(Level.INFO, "Settings: WGCustomFlagsUseRotateFlag - {0}", Settings.WGCustomFlagsUseRotateFlag);
                    logger.log(Level.INFO, "Settings: WGCustomFlagsUseSinkFlag - {0}", Settings.WGCustomFlagsUseSinkFlag);
                } else {
                    // logger.log(Level.INFO, "Movecraft did not find a compatible version of WGCustomFlags. Disabling WGCustomFlags integration.");
                }
            }
        }

        Plugin tempTownyPlugin = getServer().getPluginManager().getPlugin("Towny");
        if (tempTownyPlugin != null && tempTownyPlugin instanceof Towny) {
            logger.log(Level.INFO, "Found a compatible version of Towny. Enabling Towny integration.");
            townyPlugin = (Towny) tempTownyPlugin;
            TownyUtils.initTownyConfig();
            Settings.TownyBlockMoveOnSwitchPerm = getConfig().getBoolean("TownyBlockMoveOnSwitchPerm", false);
            Settings.TownyBlockSinkOnNoPVP = getConfig().getBoolean("TownyBlockSinkOnNoPVP", false);
            logger.log(Level.INFO, "Settings: TownyBlockMoveOnSwitchPerm - {0}", Settings.TownyBlockMoveOnSwitchPerm);
            logger.log(Level.INFO, "Settings: TownyBlockSinkOnNoPVP - {0}", Settings.TownyBlockSinkOnNoPVP);

        } else {
            // logger.log(Level.INFO, "Movecraft did not find a compatible version of Towny. Disabling Towny integration.");
        }

        Plugin tempEssentialsPlugin = getServer().getPluginManager().getPlugin("Essentials");
        if (tempEssentialsPlugin != null) {
            if (tempEssentialsPlugin.getDescription().getName().equalsIgnoreCase("essentials")) {
                if (tempEssentialsPlugin.getClass().getName().equals((String) "com.earth2me.essentials.Essentials")) {
                    if (tempEssentialsPlugin instanceof Essentials) {
                        essentialsPlugin = (Essentials) tempEssentialsPlugin;
                        // logger.log(Level.INFO, "Found a compatible version of Essentials. Enabling Essentials integration.");
                    }
                }
            }
        }
        if (essentialsPlugin == null) {
            logger.log(Level.SEVERE, "Movecraft did not find a compatible version of Essentials. THIS IS A BAD THING!");
        }

        // and now Vault
        if (getServer().getPluginManager().getPlugin("Vault") != null) {
            RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp != null) {
                economy = rsp.getProvider();
                Settings.RepairMoneyPerBlock = getConfig().getDouble("RepairMoneyPerBlock", 0.0);
                logger.log(Level.INFO, "Found a compatible Vault plugin.");
            } else {
                // logger.log(Level.INFO, "Could not find compatible Vault plugin. Disabling Vault integration.");
                economy = null;
            }
        } else {
            // logger.log(Level.INFO, "Could not find compatible Vault plugin. Disabling Vault integration.");
            economy = null;
        }
        String[] localisations = {"en", "cz", "nl"};
        for (String s : localisations) {
            if (!new File(getDataFolder()
                    + "/localisation/movecraftlang_" + s + ".properties").exists()) {
                this.saveResource("localisation/movecraftlang_" + s + ".properties", false);
            }
        }

        I18nSupport.init();
        if (shuttingDown && Settings.IGNORE_RESET) {
            logger.log(
                    Level.SEVERE,
                    String.format(I18nSupport
                            .getInternationalisedString("Startup - Error - Reload error")));
            logger.log(
                    Level.INFO,
                    String.format(I18nSupport
                            .getInternationalisedString("Startup - Error - Disable warning for reload")));
            getPluginLoader().disablePlugin(this);
        } else {

            // Startup procedure
            AsyncManager.getInstance().runTaskTimer(this, 0, 1);
            MapUpdateManager.getInstance().runTaskTimer(this, 0, 1);

            CraftManager.getInstance();

            Bukkit.getServer().getPluginManager().registerEvents(new InteractListener(), this);
            if (worldEditPlugin != null) {
                Bukkit.getServer().getPluginManager().registerEvents(new WorldEditInteractListener(), this);
            }
//			getServer().getPluginManager().registerEvents(
//					new CommandListener(), this);
            this.getCommand("release").setExecutor(new CommandListener());
            this.getCommand("pilot").setExecutor(new CommandListener());
            this.getCommand("rotateleft").setExecutor(new CommandListener());
            this.getCommand("rotateright").setExecutor(new CommandListener());
            this.getCommand("cruise").setExecutor(new CommandListener());
            this.getCommand("cruiseoff").setExecutor(new CommandListener());
            this.getCommand("craftreport").setExecutor(new CommandListener());
            this.getCommand("manoverboard").setExecutor(new CommandListener());
            this.getCommand("contacts").setExecutor(new CommandListener());

            Bukkit.getServer().getPluginManager().registerEvents(new BlockListener(), this);
            Bukkit.getServer().getPluginManager().registerEvents(new PlayerListener(), this);

            if (Settings.DisableCrates == false) {
                StorageChestItem.readFromDisk();
                StorageChestItem.addRecipie();
            }

            new MovecraftMetrics(CraftManager.getInstance().getCraftTypes().length);

            logger.log(Level.INFO, String.format(I18nSupport
                    .getInternationalisedString("Startup - Enabled message"),
                    getDescription().getVersion()));
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        Movecraft.plugin = this;
        logger = getLogger();
    }

    public static Movecraft getInstance() {
        return plugin;
    }

    public WorldGuardPlugin getWorldGuardPlugin() {
        return worldGuardPlugin;
    }

    public WorldEditPlugin getWorldEditPlugin() {
        return worldEditPlugin;
    }

    public Economy getEconomy() {
        return economy;
    }

    public Cannons getCannonsPlugin() {
        return cannonsPlugin;
    }

    public WGCustomFlagsPlugin getWGCustomFlagsPlugin() {
        return wgCustomFlagsPlugin;
    }

    public Towny getTownyPlugin() {
        return townyPlugin;
    }

    public Essentials getEssentialsPlugin() {
        return essentialsPlugin;
    }
}
