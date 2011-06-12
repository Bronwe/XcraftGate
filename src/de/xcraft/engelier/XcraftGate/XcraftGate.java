package de.xcraft.engelier.XcraftGate;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.config.Configuration;
import org.yaml.snakeyaml.Yaml;

import com.nijiko.permissions.PermissionHandler;
import com.nijikokun.bukkit.Permissions.Permissions;

import de.xcraft.engelier.XcraftGate.Commands.*;

public class XcraftGate extends JavaPlugin {
	private XcraftGatePluginListener pluginListener = new XcraftGatePluginListener(
			this);
	private XcraftGatePlayerListener playerListener = new XcraftGatePlayerListener(
			this);
	private XcraftGateCreatureListener creatureListener = new XcraftGateCreatureListener(
			this);

	public XcraftGateCreatureLimiter creatureLimiter = new XcraftGateCreatureLimiter(
			this);

	public Configuration config = null;

	public PermissionHandler permissions = null;

	public Map<String, XcraftGateGate> gates = new HashMap<String, XcraftGateGate>();
	public Map<String, String> gateLocations = new HashMap<String, String>();
	public Map<String, Boolean> justTeleported = new HashMap<String, Boolean>();
	public Map<String, Integer> creatureCounter = new HashMap<String, Integer>();

	public Logger log = Logger.getLogger("Minecraft");

	public void onDisable() {
		getServer().getScheduler().cancelTasks(this);
		saveGates();
		config.save();
	}

	public void onEnable() {
		PluginManager pm = this.getServer().getPluginManager();

		pm.registerEvent(Event.Type.PLAYER_MOVE, playerListener,
				Event.Priority.Normal, this);
		pm.registerEvent(Event.Type.CREATURE_SPAWN, creatureListener,
				Event.Priority.Normal, this);
		pm.registerEvent(Event.Type.PLUGIN_ENABLE, pluginListener,
				Event.Priority.Monitor, this);
		pm.registerEvent(Event.Type.PLUGIN_DISABLE, pluginListener,
				Event.Priority.Monitor, this);

		Plugin permissionsCheck = pm.getPlugin("Permissions");
		if (permissionsCheck != null && permissionsCheck.isEnabled()) {
			permissions = ((Permissions) permissionsCheck).getHandler();
			log.info(getNameBrackets() + "hooked into Permissions "
					+ permissionsCheck.getDescription().getVersion());
		}

		log.info(getNameBrackets() + "by Engelier loaded.");

		loadConfig();
		loadWorlds();
		loadGates();

		getServer().getScheduler().scheduleAsyncRepeatingTask(this,
				creatureLimiter, 600, 600);
		getCommand("gate").setExecutor(new CommandGate(this));
		getCommand("gworld").setExecutor(new CommandWorld(this));
	}

	public Boolean hasOpPermission(Player player, String permission) {
		if (permissions != null) {
			return permissions.has(player, permission);
		} else {
			return player.isOp();
		}
	}

	public String getLocationString(Location location) {
		if (location.getWorld() != null) {
			return location.getWorld().getName() + ","
					+ Math.floor(location.getX()) + ","
					+ Math.floor(location.getY()) + ","
					+ Math.floor(location.getZ());
		} else {
			return null;
		}
	}

	public void createGate(Location location, String name) {
		XcraftGateGate newGate = new XcraftGateGate(this, name, location);
		gates.put(name, newGate);
		gateLocations.put(getLocationString(location), name);
		saveGates();
	}

	public void createGateLink(String source, String destination) {
		gates.get(source).gateTarget = destination;
		saveGates();
	}

	public void createGateLoop(String gate1, String gate2) {
		createGateLink(gate1, gate2);
		createGateLink(gate2, gate1);
	}

	public void removeGateLink(String gate) {
		gates.get(gate).gateTarget = null;
		saveGates();
	}

	public void removeGateLoop(String gate1, String gate2) {
		removeGateLink(gate1);
		removeGateLink(gate2);
	}

	public String getNameBrackets() {
		return "[" + this.getDescription().getFullName() + "] ";
	}

	private void loadConfig() {
		File configFile = new File(getDataFolder(), "worlds.yml");

		if (!configFile.exists()) {
			if (!getDataFolder().exists()) {
				getDataFolder().mkdirs();
				getDataFolder().setExecutable(true);
				getDataFolder().setWritable(true);
			}

			try {
				configFile.createNewFile();
			} catch (Exception ex) {
				log.severe(getNameBrackets() + "unable to create config file");
				ex.printStackTrace();
			}
		}

		config = new Configuration(configFile);
		config.load();

		if (config.getKeys("worlds") == null) {
			for (World world : getServer().getWorlds()) {
				config.setProperty("worlds." + world.getName() + ".type", world
						.getEnvironment().toString().toLowerCase());
			}
		}
	}

	private void loadWorlds() {
		if (config.getKeys("worlds") == null)
			return;

		for (String thisWorld : config.getKeys("worlds")) {
			String type = config.getString("worlds." + thisWorld + ".type");
			World world = null;
			if (type.equalsIgnoreCase("normal")) {
				world = getServer().createWorld(thisWorld,
						World.Environment.NORMAL);
			} else if (type.equalsIgnoreCase("nether")) {
				world = getServer().createWorld(thisWorld,
						World.Environment.NETHER);
			} else if (type.equalsIgnoreCase("skylands")) {
				world = getServer().createWorld(thisWorld,
						World.Environment.SKYLANDS);
			} else {
				log.severe(getNameBrackets() + "invalid type for world "
						+ thisWorld + ": " + type);
			}

			if (world != null) {
				if (config.getBoolean("worlds." + thisWorld + ".allowAnimals",
						true)) {
					creatureLimiter.allowAnimals(world);
				} else {
					creatureLimiter.denyAnimals(world);
					creatureLimiter.killAllAnimals(world);
				}

				if (config.getBoolean("worlds." + thisWorld + ".allowMonsters",
						true)) {
					creatureLimiter.allowMonsters(world);
				} else {
					creatureLimiter.denyMonsters(world);
					creatureLimiter.killAllMonsters(world);
				}
			}
		}
	}

	@SuppressWarnings("unchecked")
	private void loadGates() {
		File configFile = new File(getDataFolder(), "gates.yml");

		if (!configFile.exists()) {
			log.warning(getNameBrackets()
					+ "gates file not found. Create some gates!");
			return;
		}

		try {
			Yaml yaml = new Yaml();
			Map<String, Object> gatesYaml = (Map<String, Object>) yaml
					.load(new FileInputStream(configFile));
			for (Map.Entry<String, Object> thisGate : gatesYaml.entrySet()) {
				String gateName = thisGate.getKey();
				Map<String, Object> gateData = (Map<String, Object>) thisGate
						.getValue();

				if (getServer().getWorld((String) gateData.get("world")) == null) {
					log.severe(getNameBrackets() + "gate " + gateName
							+ " found in unkown world " + gateData.get("world")
							+ ". REMOVED!");
					continue;
				}

				Location gateLocation = new Location(getServer().getWorld(
						(String) gateData.get("world")),
						(Double) gateData.get("locX"),
						(Double) gateData.get("locY"),
						(Double) gateData.get("locZ"),
						((Double) gateData.get("locYaw")).floatValue(),
						((Double) gateData.get("locP")).floatValue());

				XcraftGateGate newGate = new XcraftGateGate(this, gateName,
						gateLocation);
				if (gateData.get("target") != null)
					newGate.gateTarget = (String) gateData.get("target");

				gates.put(gateName, newGate);
				gateLocations.put(getLocationString(gateLocation), gateName);
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}

		for (Map.Entry<String, XcraftGateGate> thisGate : gates.entrySet()) {
			if (gates.get(thisGate.getValue().gateTarget) == null) {
				log.severe(getNameBrackets() + "gate " + thisGate.getKey()
						+ " has an invalid destination. Destination removed.");
				thisGate.getValue().gateTarget = null;
			}
		}

	}

	private void saveGates() {
		File configFile = new File(getDataFolder(), "gates.yml");

		if (!configFile.exists()) {
			getDataFolder().mkdir();
			getDataFolder().setWritable(true);
			getDataFolder().setExecutable(true);

			try {
				configFile.createNewFile();
			} catch (Exception ex) {
				ex.printStackTrace();
				return;
			}
		}

		Map<String, Object> toDump = new HashMap<String, Object>();

		for (Map.Entry<String, XcraftGateGate> thisGate : gates.entrySet()) {
			String gateName = thisGate.getKey();

			Map<String, Object> values = new HashMap<String, Object>();
			Location location = gates.get(gateName).gateLocation;

			values.put("world", location.getWorld().getName());
			values.put("locX", location.getX());
			values.put("locY", location.getY());
			values.put("locZ", location.getZ());
			values.put("locP", location.getPitch());
			values.put("locYaw", location.getYaw());
			values.put("target", gates.get(gateName).gateTarget);

			toDump.put(gateName, values);
		}

		Yaml yaml = new Yaml();
		String dump = yaml.dump(toDump);

		try {
			FileOutputStream fh = new FileOutputStream(configFile);
			new PrintStream(fh).println(dump);
			fh.flush();
			fh.close();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

}
