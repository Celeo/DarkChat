package com.darktidegames.celeo.darkchat;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URL;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import com.darktidegames.celeo.DarkRoles;
import com.darktidegames.celeo.clans.DarkClans;
import com.darktidegames.celeo.darkchat.Channel.ChannelType;
import com.darktidegames.empyrean.C;

import de.bananaco.bpermissions.api.ApiLayer;
import de.bananaco.bpermissions.api.CalculableType;

/**
 * DarkChatCore<br>
 * <br>
 * 
 * @author Celeo
 */
public class DarkChatCore extends JavaPlugin implements Listener
{

	private List<Chatter> chatters = new ArrayList<Chatter>();
	private List<Channel> channels = new ArrayList<Channel>();
	private File databaseFile = null;
	private Connection connection = null;

	private DarkClans darkClans = null;
	private DarkRoles darkRoles = null;

	private String tellMe = "&r&d[MSG] &f%s&f%s &d-> You&f: %s";
	private String tellOther = "&r&d[MSG] You -> &f%s&f: %s";
	private String tellRaw = "&r&d[MSG] &f%s &d-> &f%s&f: %s";
	private String toChannel = "&r[%s&f] %s%s&f: %s";

	private List<String> nopms = new ArrayList<String>();
	private List<Player> ghosting = new ArrayList<Player>();

	private Map<String, String> spelling = new HashMap<String, String>();

	private List<String> noTellsToCeleo = new ArrayList<String>();

	public boolean loggingChat = false;
	private Logger chatLogger = Logger.getLogger("DarkChat");

	@Override
	public void onLoad()
	{
		getDataFolder().mkdirs();
		if (!new File(getDataFolder(), "config.yml").exists())
		{
			getLogger().info("No configuration exists, copy over from the jar");
			saveDefaultConfig();
		}
		setupDatabases();
	}

	@Override
	public void onEnable()
	{
		setupClans();
		setupRoles();
		getServer().getPluginManager().registerEvents(this, this);
		load();
		getCommand("dc").setExecutor(this);
		getCommand("darkchat").setExecutor(this);
		getCommand("ch").setExecutor(this);
		getCommand("message").setExecutor(this);
		getCommand("msg").setExecutor(this);
		getCommand("whisper").setExecutor(this);
		getCommand("tell").setExecutor(this);
		getCommand("t").setExecutor(this);
		getCommand("r").setExecutor(this);
		getLogger().info("Enabed - version " + getDescription().getVersion());
	}

	public void logChat(String formatted)
	{
		if (loggingChat)
			chatLogger.info(formatted);
	}

	@Override
	public void onDisable()
	{
		save();
		getLogger().info("Disabled");
	}

	private void save()
	{
		List<String> names = new ArrayList<String>();
		for (Channel channel : channels)
		{
			channel.save();
			names.add(channel.getName());
		}
		getConfig().set("formatting.toChannel", toChannel);
		getConfig().set("formatting.tellOther", tellOther);
		getConfig().set("formatting.tellMe", tellMe);
		getConfig().set("settings.allChannels", names);
		getConfig().set("settings.default", getDefaultChannel().getName());
		getConfig().set("settings.noTellsToCeleo", noTellsToCeleo);
		List<String> ret = new ArrayList<String>();
		for (String key : spelling.keySet())
			ret.add(key + "<>" + spelling.get(key));
		getConfig().set("spelling", ret);
		for (Chatter chatter : chatters)
			chatter.save();
		saveConfig();
	}

	private void load()
	{
		getLogger().info("Loading from configuration");
		reloadConfig();
		chatters.clear();
		channels.clear();
		toChannel = getConfig().getString("formatting.toChannel", "[%s§f] %s%s§f: %s");
		tellOther = getConfig().getString("formatting.tellOther", "§d[MSG] You -> §f%s§f: §f%s");
		tellMe = getConfig().getString("formatting.tellMe", "§d[MSG] §f%s §f%s §d-> You§f: %s");
		noTellsToCeleo = getConfig().getStringList("settings.noTellsToCeleo");
		if (noTellsToCeleo == null)
			noTellsToCeleo = new ArrayList<String>();
		List<String> names = getConfig().getStringList("settings.allChannels");
		if (names == null || names.isEmpty())
		{
			getLogger().warning("Channel list could not be loaded from configuration");
			names = new ArrayList<String>();
			names.add("offtopic");
			names.add("global");
			names.add("staff");
			names.add("help");
			names.add("local");
			names.add("trade");
			names.add("clan");
			names.add("officer");
		}
		for (String name : names)
		{
			getLogger().info("Loading channel " + name);
			channels.add(new Channel(this, name));
		}
		for (String key : getConfig().getStringList("spelling"))
			spelling.put(key.split("<>")[0], key.split("<>")[1]);
		getLogger().info("Loaded all from configuration");
		getLogger().info("Loading player objects");
		for (Player player : getServer().getOnlinePlayers())
			joinPlayer(player);
		getLogger().info("Done loading all");
	}

	/**
	 * 
	 */
	private void setupDatabases()
	{
		databaseFile = new File(getDataFolder(), "/DarkChat.db");
		if (!databaseFile.exists())
		{
			try
			{
				databaseFile.createNewFile();
			}
			catch (IOException e)
			{
				getLogger().info("ERROR: Could not create the database file!");
			}
		}
		try
		{
			Class.forName("org.sqlite.JDBC");
			connection = DriverManager.getConnection("jdbc:sqlite:"
					+ databaseFile);
			Statement stat = connection.createStatement();
			stat.executeUpdate("CREATE TABLE IF NOT EXISTS `logs` ('by' VARCHAR(25), 'to' VARCHAR(25), 'message' TEXT, 'time' TEXT)");
			stat.close();
			getLogger().info("Connected to SQLite database and initiated.");
		}
		catch (ClassNotFoundException e)
		{
			getLogger().info("ERROR: No SQLite driver found!");
		}
		catch (SQLException e)
		{
			getLogger().info("ERROR: Error with the SQL used to create the table!");
		}
	}

	private void setupClans()
	{
		Plugin test = getServer().getPluginManager().getPlugin("DarkClans");
		if (test != null)
			darkClans = (DarkClans) test;
		else
			getLogger().info("Could not connect to DarkClans");
	}

	private void setupRoles()
	{
		Plugin test = getServer().getPluginManager().getPlugin("DarkRoles");
		if (test != null)
			darkRoles = (DarkRoles) test;
		else
			getLogger().info("Could not connect to DarkRoles");
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args)
	{
		label = label.toLowerCase();
		if (!(sender instanceof Player))
		{
			if (args == null || args.length == 0)
				return false;
			if (args[0].equalsIgnoreCase("-logs"))
			{
				if (args.length == 2)
				{
					final String what = args[1];
					sender.sendMessage("Starting ...");
					getServer().getScheduler().runTaskAsynchronously(this, new Runnable()
					{
						@Override
						public void run()
						{
							flushLogsToConsole(what, "");
						}
					});
					sender.sendMessage("Done");
				}
				else if (args.length == 3)
				{
					final String what = args[1];
					final String who = args[2];
					sender.sendMessage("Starting ...");
					getServer().getScheduler().runTaskAsynchronously(this, new Runnable()
					{
						@Override
						public void run()
						{
							flushLogsToConsole(what, who);
						}
					});
					sender.sendMessage("Done");
				}
				else
					sender.sendMessage("/dc -logs name (day)");
				return true;
			}
			return false;
		}
		Player player = (Player) sender;
		if (args == null || args.length == 0)
		{
			doHelp(player);
			return true;
		}
		if (isTellLabel(label))
		{
			if (args.length == 1)
			{
				if (args[0].equals("Celeo"))
				{
					if (noTellsToCeleo.contains(player.getName()))
					{
						player.sendMessage("§cCould not find anyone online with that name. Try §a/who");
						return true;
					}
				}
				if (player.getName().equalsIgnoreCase(args[0]))
				{
					player.sendMessage("§eIf you wish to talk to yourself, you don't have to use chat!");
					return true;
				}
				Player priv = getServer().getPlayerExact(args[0]);
				if (priv == null)
				{
					priv = getServer().getPlayer(args[0]);
					if (priv == null)
					{
						player.sendMessage("§cCould not find anyone online with that name. Try §a/who");
						return true;
					}
				}
				player.sendMessage("§7You have starting private messaging §6"
						+ priv.getName());
				getChatterObject(player).setPrivateMessaging(priv);
				return true;
			}
			Player priv = getServer().getPlayerExact(args[0]);
			if (priv == null)
			{
				priv = getServer().getPlayer(args[0]);
				if (priv == null)
				{
					player.sendMessage("§cCould not find anyone online with that name. Try §a/who");
					return true;
				}
			}
			String message = "";
			for (int i = 1; i < args.length; i++)
			{
				if (message.equals(""))
					message = args[i];
				else
					message += " " + args[i];
			}
			Chatter c = getChatterObject(player);
			Player temp = c.getPrivateMessaging();
			c.setPrivateMessaging(priv);
			onPlayerChat(new AsyncPlayerChatEvent(true, player, message, new HashSet<Player>(Arrays.asList(priv))));
			c.setPrivateMessaging(temp);
			return true;
		}
		if (label.equals("r"))
		{
			Chatter c = getChatterObject(player);
			if (c.getLastMessagedMe() != null)
			{
				Player temp = c.getPrivateMessaging();
				c.setPrivateMessaging(c.getLastMessagedMe());
				String message = "";
				for (int i = 0; i < args.length; i++)
				{
					if (message.equals(""))
						message = args[i];
					else
						message += " " + args[i];
				}
				onPlayerChat(new AsyncPlayerChatEvent(true, player, message, new HashSet<Player>(Arrays.asList(c.getLastMessagedMe()))));
				c.setPrivateMessaging(temp);
			}
			else
				player.sendMessage("§7There is no one to whom you can reply");
			return true;
		}
		String param = args[0].toLowerCase();
		if (param.equals("who"))
		{
			if ((getFocusChannel(player).getType().equals(ChannelType.CLAN) || getFocusChannel(player).getType().equals(ChannelType.CLAN_OFFICER))
					&& darkClans != null)
			{
				player.chat("/f who");
				return true;
			}
			String names = "";
			for (Player p : getFocusChannel(player).getJoined())
			{
				if (names.equals(""))
					names = p.getName();
				else
					names += ", " + p.getName();
			}
			player.sendMessage("§ePlayers in "
					+ getFocusChannel(player).getNameWithColor() + "§e:\n§a"
					+ names);
			return true;
		}
		if (param.equals("list"))
		{
			String names = "";
			for (Channel channel : channels)
			{
				if (names.equals(""))
					names = channel.getNameWithColor();
				else
					names += "§f, " + channel.getNameWithColor();
			}
			player.sendMessage("§eAll channels:\n§e" + names);
			return true;
		}
		if (param.equals("join"))
		{
			if (args.length != 2)
			{
				player.sendMessage("§e/dc §bjoin §achannel");
				return true;
			}
			for (Channel channel : channels)
			{
				if (channel.getName().equalsIgnoreCase(args[1])
						|| channel.getName().startsWith(args[1]))
				{
					if (channel.canJoin(player))
						joinChannel(player, channel, false);
					else
						player.sendMessage("§cYou do not have permission to join that channel");
					return true;
				}
			}
			player.sendMessage("§cCould not find a channel with a name starting with §e"
					+ param + "§c that you are not in.");
			player.sendMessage("§cDo §e/dc §blist§c to get a list of channels");
			return true;
		}
		if (param.equals("leave"))
		{
			if (args.length != 2)
			{
				player.sendMessage("§e/dc §bleave §achannel");
				return true;
			}
			for (Channel channel : channels)
			{
				if (channel.isJoined(player)
						&& (channel.getName().toLowerCase().equalsIgnoreCase(args[1]) || channel.getTag().toLowerCase().startsWith(args[1])))
				{
					leaveChannel(player, channel);
					player.sendMessage("§eLeft channel "
							+ channel.getNameWithColor());
					return true;
				}
			}
			player.sendMessage("§cCould not find a channel with a name starting with §e"
					+ args[1] + "§c that you are in.");
			return true;
		}
		if (param.equals("-info"))
		{
			if (args.length != 2)
			{
				player.sendMessage("§e/dc §b-info §achannel");
				return true;
			}
			Channel channel = getChannel(args[1]);
			if (channel == null)
			{
				player.sendMessage("§cCould not find a channel with that name");
				return true;
			}
			player.sendMessage(channel.getNameWithColor() + ", "
					+ channel.getTagWithColor());
			player.sendMessage(String.format("Perms_speak: %b, Perms_join: %b, Verbose: %b, Type: %s, Color: %b", Boolean.valueOf(channel.isPermsSpeak()), Boolean.valueOf(channel.isPermsJoin()), Boolean.valueOf(channel.isVerbose()), channel.getType().toString(), Boolean.valueOf(channel.isColorChat())));
			return true;
		}
		if (param.equals("ignore"))
		{
			if (args.length != 2)
			{
				player.sendMessage("§e/dc §bignore §aname");
				if (getChatterObject(player).getIgnoring().isEmpty())
					return true;
				String names = "";
				for (String name : getChatterObject(player).getIgnoring())
				{
					if (names.equals(""))
						names = name;
					else
						names += ", " + name;
				}
				player.sendMessage("§7You are ignoring the following: §c"
						+ names);
				return true;
			}
			String n = args[1];
			Chatter chatter = getChatterObject(player);
			if (chatter.isIgnoring(n))
				chatter.stopIgnoring(n);
			else
				chatter.ignore(n);
			player.sendMessage("§eYou are "
					+ (chatter.isIgnoring(n) ? "" : "no longer ")
					+ "ignoring §6" + n);
			return true;
		}
		if (param.equals("-nopms"))
		{
			if (!hasPerms(player, "darkchat.mod"))
				return true;
			if (args.length != 1)
			{
				player.sendMessage("§e/dc §bnopms");
				return true;
			}
			if (nopms.contains(player.getName()))
				nopms.remove(player.getName());
			else
				nopms.add(player.getName());
			player.sendMessage("§7You are "
					+ (nopms.contains(player.getName()) ? "not " : "")
					+ "accepting tells from non-Staff at this time.");
			return true;
		}
		if (param.equals("-ghost"))
		{
			if (!hasPerms(player, "darkchat.mod"))
				return true;
			if (args.length != 1)
			{
				player.sendMessage("§e/dc §bnopms");
				return true;
			}
			if (ghosting.contains(player))
				ghosting.remove(player);
			else
				ghosting.add(player);
			player.sendMessage("§7You are "
					+ (ghosting.contains(player) ? "" : "not ") + "ghosting.");
			return true;
		}
		if (param.equals("-mute"))
		{
			if (args.length == 3)
			{
				for (Channel channel : channels)
				{
					args[1] = args[1].toLowerCase();
					if (channel.getName().toLowerCase().equals(args[1])
							|| channel.getName().toLowerCase().startsWith(args[1]))
					{
						if (player.hasPermission("darkchat.mod")
								|| channel.isMod(player.getName()))
						{
							channel.toggleMute(args[2]);
							player.sendMessage(String.format("§c%s§e is %smuted in %s", args[2], (channel.isMuted(args[2]) ? "" : "not "), channel.getNameWithColor()));
							return true;
						}
						player.sendMessage("§cYou cannot mute in that channel");
						return true;
					}
				}
				player.sendMessage("§cCannot find the channel.");
				return true;
			}
			player.sendMessage("§e/dc §b-mute §achannel §9target");
			return true;
		}
		if (param.equals("-shadowmute"))
		{
			if (!hasPerms(player, "darkchat.admin"))
				return true;
			if (args.length == 3)
			{
				for (Channel channel : channels)
				{
					args[1] = args[1].toLowerCase();
					if (channel.getName().toLowerCase().equals(args[1])
							|| channel.getName().toLowerCase().startsWith(args[1]))
					{
						channel.toggleShadowMute(args[2]);
						player.sendMessage(String.format("§c%s§e is %sshadowmuted in %s", args[2], (channel.isShadowMuted(args[2]) ? "" : "not "), channel.getNameWithColor()));
						return true;
					}
				}
				player.sendMessage("§cCannot find the channel.");
				return true;
			}
			player.sendMessage("§e/dc §b-shadowmute §achannel §9target");
			return true;
		}
		if (param.equals("-kick"))
		{
			if (args.length == 3)
			{
				Player toKick = getServer().getPlayer(args[2]);
				if (toKick == null)
				{
					player.sendMessage("§cCould not find the player.");
					return true;
				}
				for (Channel channel : channels)
				{
					args[1] = args[1].toLowerCase();
					if (channel.getName().toLowerCase().equals(args[1])
							|| channel.getName().toLowerCase().startsWith(args[1]))
					{
						if (player.hasPermission("darkchat.mod")
								|| channel.isMod(player.getName()))
						{
							channel.removePlayer(toKick);
							player.sendMessage(String.format("§c%s§e was kicked from %s", args[2], channel.getNameWithColor()));
							return true;
						}
						player.sendMessage("§cYou cannot kick from that channel");
						return true;
					}
				}
				player.sendMessage("§cCannot find the channel.");
				return true;
			}
			player.sendMessage("§e/dc §b-kick §achannel §9target");
			return true;
		}
		if (param.equals("-ban"))
		{
			if (args.length == 3)
			{
				for (Channel channel : channels)
				{
					args[1] = args[1].toLowerCase();
					if (channel.getName().toLowerCase().equals(args[1])
							|| channel.getName().toLowerCase().startsWith(args[1]))
					{
						if (player.hasPermission("darkchat.mod")
								|| channel.isMod(player.getName()))
						{
							channel.toggleBan(args[2]);
							player.sendMessage(String.format("§c%s§e is %sbanned from %s", args[2], (channel.isBanned(args[2]) ? "" : "not "), channel.getNameWithColor()));
							return true;
						}
						player.sendMessage("§cYou cannot ban in that channel");
						return true;
					}
				}
				player.sendMessage("§cCannot find the channel.");
				return true;
			}
			player.sendMessage("§e/dc §b-ban §achannel §9target");
			return true;
		}
		if (param.equals("-reload"))
		{
			if (!hasPerms(player, "darkchat.admin"))
				return true;
			load();
			player.sendMessage("§aReloaded from configuration");
			return true;
		}
		if (param.equals("-flush") || param.equals("-save"))
		{
			if (!hasPerms(player, "darkchat.admin"))
				return true;
			save();
			player.sendMessage("§aSaved to configuration");
			return true;
		}
		if (param.equals("-create"))
		{
			if (!hasPerms(player, "darkchat.admin"))
				return true;
			if (args.length != 2)
			{
				player.sendMessage("§e/dc §b-create §achannel");
				return true;
			}
			if (getConfig().getString("channels." + args[1] + ".tag") != null)
			{
				player.sendMessage("§aLoading channel with name " + args[1]);
				channels.add(new Channel(this, args[1]));
				player.sendMessage("§aLoaded channel "
						+ getChannel(args[1]).getNameWithColor());
			}
			else
			{
				player.sendMessage("§cCould not find a channel with that name in the configuration file");
			}
			return true;
		}
		if (param.equals("-logs"))
		{
			if (!hasPerms(player, "darkchat.admin"))
				return true;
			if (args.length == 2)
			{
				final String what = args[1];
				player.sendMessage("§7Starting ...");
				getServer().getScheduler().runTaskAsynchronously(this, new Runnable()
				{
					@Override
					public void run()
					{
						flushLogsToConsole(what, "");
					}
				});
				player.sendMessage("§aDone");
			}
			else if (args.length == 3)
			{
				final String what = args[1];
				final String who = args[2];
				player.sendMessage("§7Starting ...");
				getServer().getScheduler().runTaskAsynchronously(this, new Runnable()
				{
					@Override
					public void run()
					{
						flushLogsToConsole(what, who);
					}
				});
				player.sendMessage("§aDone");
			}
			else
				player.sendMessage("§e/dc §9-logs §aname (day)");
			return true;
		}
		if (param.equals("-notellstoceleo"))
		{
			if (!player.getName().equals("Celeo"))
				return true;
			if (args.length == 1)
				player.sendMessage("§7People who cannot send you tells, ever: "
						+ C.listToString(noTellsToCeleo).replace(",", ", "));
			else if (args.length == 2)
			{
				if (noTellsToCeleo.contains(args[1]))
					noTellsToCeleo.remove(args[1]);
				else
					noTellsToCeleo.add(args[1]);
				player.sendMessage("§7"
						+ args[1]
						+ (noTellsToCeleo.contains(args[1]) ? " cannot" : " can")
						+ " send you tells");
			}
			else
				player.sendMessage("§/dc §9-notellstoceleo §a(name to add or remove)");
			return true;
		}
		if (param.equals("-readout"))
		{
			if (!hasPerms(player, "darkchat.admin"))
				return true;
			String ret = "";
			for (Chatter chatter : chatters)
			{
				if (ret.equals(""))
					ret = "c:" + chatter.getPlayer().getName();
				else
					ret += ", c:" + chatter.getPlayer().getName();
			}
			ret += "\n";
			for (Channel channel : channels)
			{
				ret += " §fr:" + channel.getNameWithColor();
			}
			player.sendMessage(ret);
			return true;
		}
		if (param.equals("-defaultconfig"))
		{
			if (!hasPerms(player, "darkchat.admin"))
				return true;
			saveDefaultConfig();
			load();
			player.sendMessage("§aSet configuration to default and loaded from disk");
			return true;
		}
		if (param.equals("-databasedamnit"))
		{
			if (!hasPerms(player, "darkchat.admin"))
				return true;
			setupDatabases();
			player.sendMessage("§aDone");
			return true;
		}
		if (param.equals("-logging"))
		{
			if (!hasPerms(player, "darkchat.admin"))
				return true;
			loggingChat = !loggingChat;
			if (loggingChat)
			{
				try
				{
					File temp = new File(getDataFolder().getAbsolutePath()
							+ "/chat.log");
					if (temp.exists())
						temp.delete();
					temp.createNewFile();
					chatLogger.setUseParentHandlers(false);
					FileHandler handler = new FileHandler(getDataFolder().getAbsolutePath()
							+ "/chat.log", true);
					handler.setFormatter(new Formatter()
					{
						@Override
						public String format(LogRecord logRecord)
						{
							return new SimpleDateFormat("MM/dd/yyyy HH:mm:ss z").format(new Date(logRecord.getMillis()))
									+ " " + logRecord.getMessage() + "\n";
						}
					});
					chatLogger.addHandler(handler);
				}
				catch (Exception e)
				{
					chatLogger.setUseParentHandlers(true);
					getLogger().severe("An error occured with setting up the chat logger.");
				}
			}
			player.sendMessage("§7" + (loggingChat ? "Logging" : "Not logging")
					+ " chat to the logger file");
			return true;
		}
		for (Channel channel : channels)
		{
			if (channel.getName().equalsIgnoreCase(param)
					|| channel.getName().startsWith(param))
			{
				if (args.length > 1)
				{
					String quickSend = "";
					for (int i = 1; i < args.length; i++)
					{
						if (quickSend.equals(""))
							quickSend = args[i];
						else
							quickSend += " " + args[i];
					}
					Channel temp = getChatterObject(player).getFocus();
					setFocus(player, channel, false);
					onPlayerChat(new AsyncPlayerChatEvent(true, player, quickSend, new HashSet<Player>(Arrays.asList(getServer().getOnlinePlayers()))));
					setFocus(player, temp, false);
				}
				else
					setFocus(player, channel, true);
				return true;
			}
		}
		player.sendMessage("§cCould not find a channel with a name starting with §e"
				+ param + "§c.");
		player.sendMessage("§cDo §e/dc §blist§c to get a list of channels");
		return true;
	}

	public static boolean isBasicLabel(String label)
	{
		return label.equalsIgnoreCase("ch") || label.equalsIgnoreCase("dc")
				|| label.equalsIgnoreCase("darkchat");
	}

	public static boolean isTellLabel(String label)
	{
		return label.equalsIgnoreCase("tell")
				|| label.equalsIgnoreCase("whisper")
				|| label.equalsIgnoreCase("msg")
				|| label.equalsIgnoreCase("message")
				|| label.equalsIgnoreCase("t");
	}

	public Channel getFocusChannel(Player player)
	{
		for (Chatter chatter : chatters)
			if (chatter.getPlayer().getName().equalsIgnoreCase(player.getName()))
				return chatter.getFocus();
		return joinPlayer(player).getFocus();
	}

	/**
	 * The player joins the channel
	 * 
	 * @param player
	 *            Player
	 * @param channel
	 *            Channel
	 * @param focus
	 *            boolean
	 */
	public void joinChannel(Player player, Channel channel, boolean focus)
	{
		Chatter chatter = getChatterObject(player);
		if (focus)
			chatter.setFocus(channel);
		else if (!channel.isJoined(player))
			chatter.joinChannel(channel);
		player.sendMessage("§eJoined channel " + channel.getNameWithColor());
	}

	public void setFocus(Player player, Channel channel, boolean verbose)
	{
		Chatter chatter = getChatterObject(player);
		chatter.setPrivateMessaging(null);
		if (!channel.isJoined(player))
		{
			player.sendMessage("§cYou are not in " + channel.getNameWithColor());
			return;
		}
		chatter.setFocus(channel);
		if (verbose)
			player.sendMessage("§eSet focus to " + channel.getNameWithColor());
	}

	public void leaveChannel(Player player, Channel channel)
	{
		getChatterObject(player).leaveChannel(channel, true);
	}

	private boolean hasPerms(Player player, String permission)
	{
		if (!player.hasPermission(permission))
		{
			player.sendMessage("§cYou cannot use that.");
			return false;
		}
		return true;
	}

	private void doHelp(Player player)
	{
		player.sendMessage("§bDarkChat§c is the chat plugin for §2Empyrean Wars.");
		player.sendMessage("§eUse §c/tell §bplayerName §amessage§e to send a private message.");
		player.sendMessage("§eUse §c/dc §bchannel§e to switch focus channels");
	}

	public Channel getDefaultChannel()
	{
		for (Channel channel : channels)
			if (channel.getName().equalsIgnoreCase(getConfig().getString("settings.default")))
				return channel;
		getLogger().severe("Default channel could not be found!");
		return null;
	}

	public List<Channel> getAllChannels()
	{
		return channels;
	}

	public Channel getChannel(String name)
	{
		for (Channel channel : channels)
			if (channel.getName().equalsIgnoreCase(name))
				return channel;
		return null;
	}

	/**
	 * 
	 * @param event
	 *            AsyncPlayerChatEvent
	 */
	@EventHandler
	public void onPlayerChat(AsyncPlayerChatEvent event)
	{
		if (event.isCancelled())
			return;

		Player player = event.getPlayer();
		Chatter chatter = getChatterObject(player);
		Channel focus = chatter.getFocus();
		event.setCancelled(true);

		if (player.hasPermission("darkchat.color"))
			event.setMessage(event.getMessage().replace("&", "§"));

		for (String str : event.getMessage().split(" "))
		{
			if (spelling.containsKey(str))
			{
				event.setMessage(event.getMessage().replace(str, spelling.get(str).replace("_", " ")));
				continue;
			}
			if (str.startsWith("http") && !str.contains("darktidegames"))
			{
				if (str.length() > 40)
				{
					try
					{
						URL url = new URL("http://darktidegames.com/l/addnew.php?link="
								+ str);
						BufferedReader in = new BufferedReader(new InputStreamReader(url.openConnection().getInputStream()));
						event.setMessage(event.getMessage().replace(str, in.readLine()));
						in.close();
					}
					catch (Exception e)
					{
						e.printStackTrace();
					}
				}
			}
		}

		if (chatter.isPrivateMessaging())
		{
			if (nopms.contains(chatter.getPrivateMessaging().getName())
					&& !player.hasPermission("darkchat.nopmsbypass"))
			{
				player.sendMessage("§cThis Staff member is not currently accepting private messages.");
				return;
			}
			if (chatter.getPrivateMessaging().getName().equals("Celeo"))
			{
				if (noTellsToCeleo.contains(player.getName()))
				{
					player.sendMessage("§cCould not find anyone online with that name. Try §a/who");
					return;
				}
			}
			String tag = "";
			if (darkRoles.getStaffType(player) != null)
				tag = getPrefix(player);
			else
			{
				tag = darkClans.getRelationTag(player.getName(), chatter.getPrivateMessaging().getName());
				if (tag != null && !tag.equals("") && !tag.equals("~"))
					tag += "-";
				else
					tag = "";
			}

			if (getChatterObject(chatter.getPrivateMessaging()).isIgnoring(chatter.getPlayer()))
			{
				if (chatter.getPlayer().hasPermission("darkchat.bypassignore"))
					chatter.getPrivateMessaging().sendMessage(String.format(tellMe.replace("&", "§"), tag, player.getName(), event.getMessage()));
			}
			else
			{
				chatter.getPrivateMessaging().sendMessage(String.format(tellMe.replace("&", "§"), tag, player.getName(), event.getMessage()));
				getChatterObject(chatter.getPrivateMessaging()).setLastMessagedMe(player);
			}
			player.sendMessage(String.format(tellOther.replace("&", "§"), chatter.getPrivateMessaging().getName(), event.getMessage()));
			store(player.getName(), chatter.getPrivateMessaging().getName(), event.getMessage());
			List<Player> toGhosts = new ArrayList<Player>();
			toGhosts.add(chatter.getPlayer());
			toGhosts.add(chatter.getPrivateMessaging());
			notifyGhosts(String.format(tellRaw.replace("&", "§"), chatter.getPlayer().getName(), chatter.getPrivateMessaging().getName(), event.getMessage()), toGhosts);
			return;
		}

		if (!focus.canSpeak(player))
		{
			player.sendMessage("§cYou cannot speak in channel "
					+ focus.getNameWithColor());
			return;
		}

		String prefix = "";
		String tag = "";
		boolean staff = false;
		if (darkRoles.getStaffType(player) != null)
		{
			staff = true;
			tag = getPrefix(player);
		}
		if (!staff)
			prefix = ApiLayer.getValue("world", CalculableType.USER, player.getName(), "prefix").replace("&", "§");

		/*
		 * tag is staff position or clan/clan title (if in a clan), prefix is
		 * custom titles and color (if not using staff tag)
		 */

		List<Player> listening = chatter.getFocus().getRecipients(player);
		if (!darkClans.isInClan(player.getName())
				&& (chatter.getFocus().getType().equals(ChannelType.CLAN) || chatter.getFocus().getType().equals(ChannelType.CLAN_OFFICER)))
		{
			player.sendMessage("§7You are not in a clan, so no one will hear you speak in this channel");
			listening.add(player);
		}
		for (Player recipient : listening)
		{
			if (getChatterObject(recipient).isIgnoring(player)
					&& !player.hasPermission("darkchat.mod"))
				continue;
			if (!staff)
			{
				if (focus.getType().equals(ChannelType.CLAN)
						|| focus.getType().equals(ChannelType.CLAN_OFFICER))
				{
					String temp = darkClans.getTitleFor(player);
					if (!temp.equals(""))
						tag = "§9" + temp + "§f-";
					recipient.sendMessage(String.format(toChannel.replace("&", "§"), "§a"
							+ darkClans.getClanFor(player.getName()).getName().substring(0, 3), tag
							+ prefix, player.getName(), (focus.isColorChat() ? focus.getColorFormatted() : "")
							+ event.getMessage()));
				}
				else
				{
					tag = darkClans.getRelationTag(player.getName(), recipient.getName());
					if (tag != null && !tag.equals("") && !tag.equals("~"))
						tag += "-";
					else
						tag = "";
					tag = tag.replace("&", "§");
					recipient.sendMessage(String.format(toChannel.replace("&", "§"), focus.getTagWithColor(), tag
							+ prefix, player.getName(), (focus.isColorChat() ? focus.getColorFormatted() : "")
							+ event.getMessage()));
				}
			}
			else
				recipient.sendMessage(String.format(toChannel.replace("&", "§"), focus.getTagWithColor(), tag
						+ prefix, player.getName(), (focus.isColorChat() ? focus.getColorFormatted() : "")
						+ event.getMessage()));
		}
		store(player.getName(), chatter.getFocus().getName(), event.getMessage());
		notifyGhosts(String.format(toChannel.replace("&", "§"), focus.getTagWithColor(), tag
				+ prefix, player.getName(), (focus.isColorChat() ? focus.getColorFormatted() : "")
				+ event.getMessage()), listening);
	}

	private void notifyGhosts(String formatted, List<Player> received)
	{
		for (Player g : ghosting)
		{
			if (g == null || !g.isOnline())
				continue;
			if (!received.contains(g))
				g.sendMessage("§8<G> §f" + formatted);
		}
	}

	/**
	 * This could be written better
	 * 
	 * @param player
	 *            Player
	 * @return String prefix for the player based on their staff role
	 */
	public String getPrefix(Player player)
	{
		DarkRoles.StaffType staff = darkRoles.getStaffType(player);
		if (staff == null)
			return ApiLayer.getValue("world", CalculableType.USER, player.getName(), "prefix").replace("&", "§");
		if (darkRoles.getStaffType(player).equals(DarkRoles.StaffType.ADMIN))
			return ApiLayer.getValue("world", CalculableType.GROUP, "admin", "prefix").replace("&", "§");
		else if (darkRoles.getStaffType(player).equals(DarkRoles.StaffType.MODERATOR))
			return ApiLayer.getValue("world", CalculableType.GROUP, "moderator", "prefix").replace("&", "§");
		else if (darkRoles.getStaffType(player).equals(DarkRoles.StaffType.GUIDE))
			return ApiLayer.getValue("world", CalculableType.GROUP, "guide", "prefix").replace("&", "§");
		else
			return "";
	}

	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent event)
	{
		Player player = event.getPlayer();
		removePlayer(player);
		for (Chatter chatter : getChatters())
			if (chatter.isPrivateMessaging()
					&& chatter.getPrivateMessaging().getName().equals(player.getName()))
				chatter.setPrivateMessaging(null);
	}

	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent event)
	{
		joinPlayer(event.getPlayer());
	}

	public Chatter joinPlayer(Player player)
	{
		Chatter chatter = new Chatter(this, player);
		chatters.add(chatter);
		return chatter;
	}

	public void removePlayer(Player player)
	{
		List<Chatter> ret = new ArrayList<Chatter>();
		for (Chatter chatter : chatters)
		{
			if (chatter.getPlayer().getName().equals(player.getName()))
				chatter.close();
			else
				ret.add(chatter);
		}
		chatters.clear();
		for (Chatter chatter : ret)
			chatters.add(chatter);
	}

	public Chatter getChatterObject(Player player)
	{
		for (Chatter chatter : chatters)
			if (chatter.getPlayer().getName().equalsIgnoreCase(player.getName()))
				return chatter;
		return joinPlayer(player);
	}

	/**
	 * Stores the message by player to player or channel in the database
	 * 
	 * @param by
	 *            String
	 * @param to
	 *            String
	 * @param message
	 *            String
	 */
	public void store(String by, String to, String message)
	{
		boolean isChannel = false;
		message = message.replace("'", "''");
		message = ChatColor.stripColor(message);
		for (Channel channel : channels)
			if (channel.getName().equals(to))
				isChannel = true;
		try
		{
			Statement stat = connection.createStatement();
			stat.execute(String.format("Insert into `logs` (`by`, `to`, `message`, `time`) values ('%s', '%s', '%s' ,'%s')", by, (isChannel ? "c:"
					+ to : to), message, Long.valueOf(System.currentTimeMillis()).toString()));
			stat.close();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		logChat(String.format("%s -> %s: %s", by, to, message));
	}

	public List<Channel> getChannels()
	{
		return channels;
	}

	public void setChannels(List<Channel> channels)
	{
		this.channels = channels;
	}

	public String getToChannel()
	{
		return toChannel;
	}

	public void setToChannel(String toChannel)
	{
		this.toChannel = toChannel;
	}

	public String getTellOther()
	{
		return tellOther;
	}

	public void setTellOther(String tellOther)
	{
		this.tellOther = tellOther;
	}

	public String getTellMe()
	{
		return tellMe;
	}

	public void setTellMe(String tellMe)
	{
		this.tellMe = tellMe;
	}

	public void setChatters(List<Chatter> chatters)
	{
		this.chatters = chatters;
	}

	private void flushLogsToConsole(String by, String dateMatch)
	{
		try
		{
			File file = new File(getDataFolder(), by.replace(":", ".")
					+ "_logs" + (dateMatch.equals("") ? "" : "_" + dateMatch)
					+ ".txt");
			file.delete();
			file.createNewFile();
			PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(file)));
			Statement stat = connection.createStatement();
			ResultSet rs;
			if (by.equals("-all"))
				rs = stat.executeQuery("Select * from `logs`");
			else if (by.startsWith("c:"))
				rs = stat.executeQuery("Select * from `logs` where `to` like '"
						+ by + "%'");
			else
				rs = stat.executeQuery(String.format("Select * from `logs` where `by`='%s' or `to`='%s'", by, by));
			SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yy HH:mm:ss");
			String t = "";
			while (rs.next())
			{
				t = sdf.format(new Date(rs.getLong("time")));
				if (!dateMatch.equals("")
						&& !t.replace("/", "").replace("(", "").startsWith(dateMatch))
					continue;
				writer.write(String.format("(%s) %s -> %s: %s\n", t, rs.getString("by"), rs.getString("to"), rs.getString("message")));
			}
			writer.write("\n\nTimestamp: "
					+ sdf.format(new Date(System.currentTimeMillis())));
			writer.flush();
			writer.close();
			rs.close();
			stat.close();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	public List<Chatter> getChatters()
	{
		return chatters;
	}

	public DarkClans getDarkClans()
	{
		return darkClans;
	}

	public DarkRoles getDarkRoles()
	{
		return darkRoles;
	}

}