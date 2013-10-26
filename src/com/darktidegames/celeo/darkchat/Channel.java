package com.darktidegames.celeo.darkchat;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * <b>Channel</b><br>
 * 
 * @author Celeo
 */
public class Channel
{

	/*
	 * Persistant
	 */

	private final DarkChatCore plugin;
	private final String name;
	private String tag;
	private String color = "f";
	private ChannelType type = ChannelType.NORMAL;
	private boolean permsJoin = false;
	private boolean permsSpeak = false;
	private boolean verbose = false;
	private boolean canLeave = true;
	private List<String> muted = new ArrayList<String>();
	private List<String> banned = new ArrayList<String>();
	private List<String> mods = new ArrayList<String>();
	private List<Player> joined = new ArrayList<Player>();
	private boolean colorChat = false;
	private List<String> shadowMuted = new ArrayList<String>();

	/*
	 * Runtime
	 */


	public Channel(DarkChatCore plugin, String name)
	{
		this.plugin = plugin;
		this.name = name;
		load();
	}

	private void load()
	{
		String p = "channels." + name + ".";
		tag = plugin.getConfig().getString(p + "tag", String.valueOf(name.charAt(0)));
		color = plugin.getConfig().getString(p + "color", "f");
		type = ChannelType.get(plugin.getConfig().getString(p + "type", "normal"));
		permsJoin = plugin.getConfig().getBoolean(p + "perms.join", false);
		permsSpeak = plugin.getConfig().getBoolean(p + "perms.speak", false);
		verbose = plugin.getConfig().getBoolean(p + "verbose", false);
		canLeave = plugin.getConfig().getBoolean(p + "canleave", true);
		colorChat = plugin.getConfig().getBoolean(p + "colorChat", false);
		muted = plugin.getConfig().getStringList(p + "muted");
		banned = plugin.getConfig().getStringList(p + "banned");
		mods = plugin.getConfig().getStringList(p + "mods");
		if (muted == null)
			muted = new ArrayList<String>();
		if (banned == null)
			banned = new ArrayList<String>();
		if (mods == null)
			mods = new ArrayList<String>();
	}

	public void save()
	{
		String p = "channels." + name + ".";
		plugin.getConfig().set(p + "tag", tag);
		plugin.getConfig().set(p + "color", color);
		plugin.getConfig().set(p + "type", type.toString());
		plugin.getConfig().set(p + "perms.join", Boolean.valueOf(permsJoin));
		plugin.getConfig().set(p + "perms.speak", Boolean.valueOf(permsSpeak));
		plugin.getConfig().set(p + "verbose", Boolean.valueOf(verbose));
		plugin.getConfig().set(p + "canleave", Boolean.valueOf(canLeave));
		plugin.getConfig().set(p + "colorChat", Boolean.valueOf(colorChat));
		plugin.getConfig().set(p + "muted", muted);
		plugin.getConfig().set(p + "banned", banned);
		plugin.getConfig().set(p + "mods", mods);
		plugin.saveConfig();
	}

	/**
	 * 
	 * @param player
	 *            Player
	 * @return True if the player can join this channel
	 */
	public boolean canJoin(Player player)
	{
		if (isBanned(player.getName()))
			return false;
		if (permsJoin)
			return player.hasPermission("darkchat." + name + ".join")
					|| player.hasPermission("darkchat.mod");
		return true;
	}

	/**
	 * 
	 * @param player
	 *            Player
	 * @return True if the player can speak in this channel
	 */
	public boolean canSpeak(Player player)
	{
		if (isMuted(player.getName()))
			return false;
		if (isBanned(player.getName()))
			return false;
		if (permsSpeak)
			return player.hasPermission("darkchat." + name + ".speak")
					|| player.hasPermission("darkchat.mod");
		return true;
	}

	public void joinPlayer(Player player)
	{
		joinPlayerSilent(player);
		if (!verbose)
			return;
		for (Player p : joined)
			p.sendMessage(String.format("[%s§f]: §%s%s has joined the channel", getNameWithColor(), color, player.getName()));
	}

	public void joinPlayerSilent(Player player)
	{
		if (joined.contains(player))
			throw new IllegalArgumentException("The player is already in that channel!");
		joined.add(player);
	}

	public void removePlayer(Player player)
	{
		removePlayerSilent(player);
		if (!verbose)
			return;
		for (Player p : joined)
			p.sendMessage(String.format("[%s§f]: §%s%s has left the channel", getNameWithColor(), color, player.getName()));
	}

	public void removePlayerSilent(Player player)
	{
		if (!joined.contains(player))
			throw new IllegalArgumentException("The player is not in that channel!");
		joined.remove(player);
	}

	@SuppressWarnings("boxing")
	public List<Player> getPlayersInsideArea(Location origin, int distance, List<Player> available)
	{
		List<Player> ret = new ArrayList<Player>();
		Location loc = null;
		Double x = null;
		Double y = null;
		Double z = null;
		for (Player player : available)
		{
			loc = player.getLocation();
			if (!loc.getWorld().getName().equals(origin.getWorld().getName()))
				continue;
			x = loc.getX() - origin.getX();
			y = loc.getY() - origin.getY();
			z = loc.getZ() - origin.getZ();
			if (Math.sqrt((x * x) + (y * y) + (z * z)) <= distance)
				ret.add(player);
		}
		return ret;
	}

	public String getTag()
	{
		return tag;
	}

	public void setTag(String tag)
	{
		this.tag = tag;
	}

	public String getColor()
	{
		return color;
	}

	public void setColor(String color)
	{
		this.color = color;
	}

	public ChannelType getType()
	{
		return type;
	}

	public void setType(ChannelType type)
	{
		this.type = type;
	}

	public List<Player> getJoined()
	{
		return joined;
	}

	public List<Player> getRecipients(Player player)
	{
		List<Player> ret = new ArrayList<Player>();
		if (isShadowMuted(player.getName()))
		{
			ret.add(player);
			return ret;
		}
		switch (type)
		{
		case LOCAL:
			return getPlayersInsideArea(player.getLocation(), 50, joined);
		case CLAN:
			if (plugin.getDarkClans().getClanFor(player.getName()) == null)
				return ret;
			for (Player online : plugin.getDarkClans().getOnlinePlayersInClan(plugin.getDarkClans().getClanFor(player.getName()).getName()))
				ret.add(online);
			return ret;
		case CLAN_OFFICER:
			if (plugin.getDarkClans().getClanFor(player.getName()) == null)
				return ret;
			for (Player online : plugin.getDarkClans().getOnlineOfficersInClan(plugin.getDarkClans().getClanFor(player.getName()).getName()))
				ret.add(online);
			return ret;
		default:
		case NORMAL:
			return joined;
		}
	}

	/**
	 * 
	 * @param one
	 *            Player
	 * @param two
	 *            Player
	 * @param distance
	 *            int
	 * @return True if the two players are inside the distance
	 */
	public static boolean isInDistance(Player one, Player two, int distance)
	{
		double xone = one.getLocation().getX();
		double xtwo = two.getLocation().getX();
		double yone = one.getLocation().getY();
		double ytwo = two.getLocation().getY();
		return Math.sqrt(Math.pow(xone - xtwo, 2) + Math.pow(yone - ytwo, 2)) <= distance;
	}

	public void setJoined(List<Player> joined)
	{
		this.joined = joined;
	}

	public DarkChatCore getPlugin()
	{
		return plugin;
	}

	public String getName()
	{
		return name;
	}

	public List<String> getMuted()
	{
		return muted;
	}

	public List<String> getShadowMuted()
	{
		return shadowMuted;
	}

	public boolean isColorChat()
	{
		return colorChat;
	}

	public void setColorChat(boolean colorChat)
	{
		this.colorChat = colorChat;
	}

	public List<String> getBanned()
	{
		return banned;
	}

	public void setMuted(List<String> muted)
	{
		this.muted = muted;
	}

	public void setShadowMuted(List<String> shadowMuted)
	{
		this.shadowMuted = shadowMuted;
	}

	public boolean isPermsJoin()
	{
		return permsJoin;
	}

	public void setPermsJoin(boolean perms_join)
	{
		this.permsJoin = perms_join;
	}

	public boolean isPermsSpeak()
	{
		return permsSpeak;
	}

	public void setPermsSpeak(boolean perms_speak)
	{
		this.permsSpeak = perms_speak;
	}

	public void setBanned(List<String> banned)
	{
		this.banned = banned;
	}

	public boolean isVerbose()
	{
		return verbose;
	}

	public void setVerbose(boolean verbose)
	{
		this.verbose = verbose;
	}

	public boolean isCanLeave()
	{
		return canLeave;
	}

	public void setCanLeave(boolean canLeave)
	{
		this.canLeave = canLeave;
	}

	public boolean isJoined(Player player)
	{
		return joined.contains(player);
	}

	public boolean isMuted(String playerName)
	{
		return muted.contains(playerName);
	}

	public boolean isShadowMuted(String playerName)
	{
		return shadowMuted.contains(playerName);
	}

	public boolean isBanned(String playerName)
	{
		return banned.contains(playerName);
	}

	public void toggleMute(String playerName)
	{
		if (!muted.remove(playerName))
			muted.add(playerName);
	}

	public void toggleShadowMute(String playerName)
	{
		if (!shadowMuted.remove(playerName))
			shadowMuted.add(playerName);
	}

	public void toggleBan(String playerName)
	{
		if (!banned.remove(playerName))
			banned.add(playerName);
	}

	public String getColorFormatted()
	{
		return "§" + color;
	}

	public String getTagWithColor()
	{
		return String.format("§%s%s", color, tag);
	}

	public String getNameWithColor()
	{
		return String.format("§%s%s", color, name);
	}

	public List<String> getMods()
	{
		return mods;
	}

	public void setMods(List<String> mods)
	{
		this.mods = mods;
	}

	public boolean isMod(String playerName)
	{
		return mods.contains(playerName);
	}

	public void toggleMod(String playerName)
	{
		if (!mods.remove(playerName))
			mods.add(playerName);
	}

	public enum ChannelType
	{
		NORMAL, LOCAL, CLAN, CLAN_OFFICER;
		public static ChannelType get(String string)
		{
			for (ChannelType ct : ChannelType.values())
				if (ct.name().equalsIgnoreCase(string))
					return ct;
			return null;
		}
	}

}