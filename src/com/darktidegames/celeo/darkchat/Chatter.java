package com.darktidegames.celeo.darkchat;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.entity.Player;

import com.darktidegames.empyrean.ConfigAccessor;

/**
 * <b>Chatter</b><br>
 * 
 * @author Celeo
 */
public class Chatter
{

	private final DarkChatCore plugin;
	private final Player player;
	private List<Channel> channels;
	private Channel focus;
	private List<String> ignoring;

	private Player privateMessaging = null;
	private final ConfigAccessor config;
	private Player lastMessagedMe = null;

	/**
	 * Constructor. Creates the object, loads from memory, and joins appropriate
	 * channels as the user left them (or default)
	 * 
	 * @param plugin
	 *            DarkChatCore
	 * @param player
	 *            Player
	 */
	public Chatter(DarkChatCore plugin, Player player)
	{
		this.plugin = plugin;
		this.player = player;
		this.channels = new ArrayList<Channel>();
		this.focus = plugin.getDefaultChannel();
		this.ignoring = new ArrayList<String>();
		this.config = new ConfigAccessor(plugin, player.getName());
		load();
	}

	/*
	 * Functions
	 */

	private void load()
	{
		if (config.getConfig().getStringList("channels") == null
				|| config.getConfig().getStringList("channels").isEmpty())
		{
			for (Channel channel : plugin.getAllChannels())
			{
				if (channel.canJoin(player))
				{
					joinChannelSilent(channel);
				}
			}
		}
		else
		{
			for (String name : config.getConfig().getStringList("channels"))
			{
				if (plugin.getChannel(name) != null)
				{
					joinChannel(plugin.getChannel(name));
				}
			}
		}
		if (plugin.getChannel(config.getConfig().getString("focus")) != null)
		{
			focus = plugin.getChannel(config.getConfig().getString("focus"));
		}
		else
		{
			focus = plugin.getDefaultChannel();
		}
		ignoring = config.getConfig().getStringList("ignoring");
		if (ignoring == null)
			ignoring = new ArrayList<String>();
	}

	public void save()
	{
		List<String> names = new ArrayList<String>();
		for (Channel channel : channels)
			names.add(channel.getName());
		config.getConfig().set("channels", names);
		config.getConfig().set("focus", focus.getName());
		config.getConfig().set("ignoring", ignoring);
		config.saveConfig();
	}

	public void close()
	{
		save();
		for (Channel channel : channels)
		{
			leaveChannel(channel, false);
		}
	}

	/**
	 * Assumes this player is NOT already in the channel
	 * 
	 * @param channel
	 *            Channel
	 */
	public Channel joinChannel(Channel channel)
	{
		channels.add(channel);
		channel.joinPlayer(player);
		return channel;
	}

	/**
	 * Assumes this player is NOT already in the channel
	 * 
	 * @param channel
	 *            Channel
	 */
	private Channel joinChannelSilent(Channel channel)
	{
		channels.add(channel);
		channel.joinPlayerSilent(player);
		return channel;
	}

	/**
	 * Assumes this player IS already in the channel
	 * 
	 * @param channel
	 *            Channel
	 */
	public void leaveChannel(Channel channel, boolean remove)
	{
		channel.removePlayer(player);
		if (remove)
			channels.remove(channel);
		if (focus.getName().equals(channel.getName()))
		{
			focus = channels.size() > 0 ? channels.get(0) : joinChannelSilent(plugin.getDefaultChannel());
			player.sendMessage("§eFocus set on " + focus.getNameWithColor());
		}
	}

	/*
	 * GET and SET
	 */

	public DarkChatCore getPlugin()
	{
		return plugin;
	}

	public List<Channel> getChannels()
	{
		return channels;
	}

	public void setChannels(List<Channel> channels)
	{
		this.channels = channels;
	}

	public Channel getFocus()
	{
		return focus;
	}

	public void setFocus(Channel focus)
	{
		this.focus = focus;
		if (!focus.isJoined(player))
			joinChannel(focus);
	}

	public Player getPlayer()
	{
		return player;
	}

	public Player getPrivateMessaging()
	{
		return privateMessaging;
	}

	public void setPrivateMessaging(Player privateMessaging)
	{
		this.privateMessaging = privateMessaging;
	}

	public boolean isPrivateMessaging()
	{
		return privateMessaging != null;
	}

	public List<String> getIgnoring()
	{
		return ignoring;
	}

	/**
	 * 
	 * @param player
	 *            Player
	 * @return True if this Chatter is ignoring the passed player
	 */
	public boolean isIgnoring(Player player)
	{
		return ignoring.contains(player.getName());
	}

	/**
	 * 
	 * @param playerName
	 *            String
	 * @return true if this Chatter is ignoring the passed player's name
	 */
	public boolean isIgnoring(String playerName)
	{
		return ignoring.contains(playerName);
	}

	/**
	 * Removes the name from this chatter's ignore list
	 * 
	 * @param playerName
	 *            String
	 */
	public void stopIgnoring(String playerName)
	{
		ignoring.remove(playerName);
	}

	/**
	 * Adds the name to this Chatter's ignore list<br>
	 * <br>
	 * <b>Note: Check first <i>isIgnoring(String playerName)</i> to avoid
	 * multiple entries
	 * 
	 * @param playerName
	 *            String
	 */
	public void ignore(String playerName)
	{
		ignoring.add(playerName);
	}

	public ConfigAccessor getConfig()
	{
		return config;
	}

	public Player getLastMessagedMe()
	{
		return lastMessagedMe;
	}

	public void setLastMessagedMe(Player lastMessagedMe)
	{
		this.lastMessagedMe = lastMessagedMe;
	}

}