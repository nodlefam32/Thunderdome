package main;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin implements Listener {

	/*
	 * I Apologize if the syntax is not consistent as I have a habit of doing 
	 * public void smth() {
	 * }
	 * Instead of
	 * public void smth()
	 * {
	 * }
	 * which is arguably the better way
	 * 
	 * also please forgive my bad coding skills, I am VERY rusty.
	 * 
	 * TODO
	 * 
	 * add 1v1 mode
	 * add party vs party mode
	 * 
	 */
	
	// Variables
	
	FileConfiguration config = this.getConfig();
	private ArrayList<UUID> tournamentMMQ = new ArrayList<UUID>(); // MMQ stands for match making queue
	private ArrayList<UUID> tournamentParticipants = new ArrayList<UUID>(); // Players who have yet to die in the current cycle
	private ArrayList<UUID> tournamentWaiting = new ArrayList<UUID>(); // Players who have died and are waiting for everyone else to fight
	private ArrayList<UUID> tournamentFighting = new ArrayList<UUID>(); // The two current players fighting
	private HashMap<UUID, Integer> tournamentScore = new HashMap<UUID, Integer>(); // The score of all tournament players
	private HashMap<UUID, Location> preTournamentLocation = new HashMap<UUID, Location>(); // To teleport players back after the tournament
	private ArrayList<ItemStack> prizes = new ArrayList<ItemStack>();
	private boolean tournamentRunning = false;
	private boolean autoStart;
	private int requiredMMQSize;
	private int winningScore; 
	private int experienceBoost;
	private Location spectatorLobby = new Location(null, 0, 0, 0);
	private Location posistionOne = new Location(null, 0, 0, 0);
	private Location posistionTwo = new Location(null, 0, 0, 0);
	
	
	public void onEnable() 
	{
		config.options().copyDefaults(false); // I will leave this false to not copy stupid :v HeAvy Is DeAD!
		
		if (getConfig().contains("game.autostart")) // Checks if autostart boolean exists if it does read it if not create it as false
		{
			autoStart = config.getBoolean("game.autostart");
		} else
		{
			config.set("game.autostart", false);
			autoStart = false;
		}
		
		if (getConfig().contains("game.MMQSize")) // Check if MMQSize integer exists if it does read it if not create it as 1
		{
			requiredMMQSize = config.getInt("game.MMQSize");
			if (requiredMMQSize < 1) // If it is set below 1 for some reason; silly admins
			{
				config.set("game.MMQSize", 1);
				requiredMMQSize = 1;
			}
		} else
		{
			config.set("game.MMQSize", 1);
			requiredMMQSize = 1;
		}
		
		if (getConfig().contains("game.winningScore")) // Check if winningScore integer exists if it does read it if not create it as 3
		{
			winningScore = config.getInt("game.winningScore");
			if (winningScore < 3) // If it is set below 3 for some reason; silly admins
			{
				config.set("winningScore", 3);
				winningScore = 3;
			}
		} else
		{
			config.set("game.winningScore", 3);
			winningScore = 3;
		}
		
		if (getConfig().contains("game.experienceBoost")) // Check if winningScore integer exists if it does read it if not create it as 3
		{
			experienceBoost = config.getInt("game.experienceBoost");
			if (experienceBoost < 0) // If it is set below 1 for some reason; silly admins
			{
				config.set("experienceBoost", 125);
				experienceBoost = 125;
			}
		} else
		{
			config.set("game.experienceBoost", 125);
			experienceBoost = 125;
		}
		
		Bukkit.getPluginManager().registerEvents(this, this);
		matchMakingSystem();
		saveConfig();
	}
	
	
	public void onDisable()
	{
		saveConfig();
	}
	
	
	@EventHandler
	public void onQuit(PlayerQuitEvent event) // If a player quits remove them from the tournament
	{
		Player player = event.getPlayer();
		if (tournamentMMQ.contains(player.getUniqueId()))
		{
			tournamentMMQ.remove(player.getUniqueId());
		}
		
		if (tournamentParticipants.contains(player.getUniqueId()))
		{
			tournamentParticipants.remove(player.getUniqueId());
		}
		
		if (tournamentWaiting.contains(player.getUniqueId()))
		{
			tournamentWaiting.remove(player.getUniqueId());
		}
		
		if (tournamentFighting.contains(player.getUniqueId()))
		{
			tournamentFighting.remove(player.getUniqueId());
			
			UUID victor = tournamentFighting.get(0);
			if (tournamentScore.containsKey(victor))
			{
				tournamentScore.replace(victor, tournamentScore.get(victor) + 1);
			}
		}
		
		if (tournamentScore.containsKey(player.getUniqueId()))
		{
			tournamentScore.remove(player.getUniqueId());
		}
		
		if (preTournamentLocation.containsKey(player.getUniqueId()))
		{
			player.teleport(preTournamentLocation.get(player.getUniqueId()));
			preTournamentLocation.remove(player.getUniqueId());
		}
	}
	
	
	@EventHandler
	public void onDeath(PlayerDeathEvent event)
	{
		Player player = (Player) event.getEntity();
		if (tournamentScore.containsKey(player.getUniqueId())) // lets the player keep their items and levels throughout the tournament
		{
			if (tournamentFighting.contains(player.getUniqueId()))
			{
				tournamentWaiting.add(player.getUniqueId());
				tournamentFighting.remove(player.getUniqueId());
			}
			event.setKeepInventory(true);
			event.setKeepLevel(true);
			event.getDrops().clear(); // Prevents dupe in the thunder dome!
			event.setDeathMessage(event.getDeathMessage() + " honorably in the thunderdome!");
			player.setHealth(20);
			player.setFoodLevel(20);
			player.teleport(spectatorLobby); // Send them back to being spectator
		
			UUID victor = tournamentFighting.get(0);
			if (tournamentScore.containsKey(victor))
			{
				tournamentScore.replace(victor, tournamentScore.get(victor) + 1);
				Bukkit.broadcastMessage("" + player.getName() + " has " + tournamentScore.get(victor) + "/" + winningScore + " kills");
				for (Player player1 : Bukkit.getOnlinePlayers())
				{
					if (tournamentFighting.contains(player1.getUniqueId()))
					{
						player1.teleport(posistionOne);
						player1.setHealth(20);
						player1.setFoodLevel(20);
					}
				}
				
				// Game cycle logic
				
				if (tournamentParticipants.size() > 0)
				{
					tournamentFighting.add(tournamentParticipants.get(0));
					for (Player player1 : Bukkit.getOnlinePlayers())
					{
						if (tournamentParticipants.get(0) == player1.getUniqueId())
						{
							player1.teleport(posistionTwo);
							player1.setHealth(20);
							player1.setFoodLevel(20);
						}
					}
					tournamentParticipants.remove(tournamentParticipants.get(0));
				} else
				{
					for (Player player1 : Bukkit.getOnlinePlayers())
					{
						if (tournamentWaiting.contains(player1.getUniqueId()))
						{
							tournamentWaiting.remove(player1.getUniqueId());
							tournamentParticipants.add(player1.getUniqueId());
						}
					}
					
					tournamentFighting.add(tournamentParticipants.get(0));
					for (Player player1 : Bukkit.getOnlinePlayers())
					{
						if (tournamentParticipants.get(0) == player1.getUniqueId())
						{
							player1.teleport(posistionTwo);
							player1.setHealth(20);
							player1.setFoodLevel(20);
						}
					}
					tournamentParticipants.remove(tournamentParticipants.get(0));
				}
				if (tournamentScore.get(victor) >= winningScore)
				{
					Bukkit.broadcastMessage("The tournament has ended!");
					
					// Clear relevant arrays
					
					tournamentParticipants.clear();
					tournamentWaiting.clear();
					tournamentScore.clear();
					tournamentFighting.clear();
					tournamentRunning = false;
					
					// Give prize to winner
					
					for (Player player1 : Bukkit.getOnlinePlayers()) // Needs to be modified to work if inventory full
					{
						if (preTournamentLocation.containsKey(player1.getUniqueId())) // Teleport all players back to where they were before the event
						{
							player1.setHealth(20);
							player1.setFoodLevel(20);
							player1.teleport(preTournamentLocation.get(player1.getUniqueId()));
							preTournamentLocation.remove(player1.getUniqueId());
						}
						
						if (player1.getUniqueId() == victor)
						{
							player1.getInventory().addItem(prizes.get(0));
							player1.setTotalExperience(player1.getTotalExperience() + experienceBoost);
						}
					}
				}
			}
		}
	}
	
	
	public void matchMakingSystem()  // Starts tournaments
	{
	Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable() 
		{
			@Override
			public void run() 
			{
				// This code runs every 30 seconds and checks if a tournament game can automatically start.
				if (tournamentMMQ.size() > requiredMMQSize && autoStart == true && tournamentRunning == false)
				{
					if (config.contains("game.spectatorLobby"))
					{
						if (config.contains("game.position1"))
						{
							if (config.contains("game.position2"))
							{
							
							for (World world : Bukkit.getWorlds()) // get the world to teleport the player to
							{
								if (config.getString("game.spectatorLobby.world").equals(world.getName()))
								{
									spectatorLobby.setWorld(world);
									spectatorLobby.setX((double) config.getInt("game.spectatorLobby.x"));
									spectatorLobby.setY((double) config.getInt("game.spectatorLobby.y"));
									spectatorLobby.setZ((double) config.getInt("game.spectatorLobby.z"));
								}
								
								if (config.getString("game.position1.world").equals(world.getName()))
								{
									posistionOne.setWorld(world);
									posistionOne.setX((double) config.getInt("game.position1.x"));
									posistionOne.setY((double) config.getInt("game.position1.y"));
									posistionOne.setZ((double) config.getInt("game.position1.z"));
								}
								
								if (config.getString("game.position2.world").equals(world.getName()))
								{
									posistionTwo.setWorld(world);
									posistionTwo.setX((double) config.getInt("game.position2.x"));
									posistionTwo.setY((double) config.getInt("game.position2.y"));
									posistionTwo.setZ((double) config.getInt("game.position2.z"));
								}
							}
							Bukkit.broadcastMessage("Starting thunderdome tournament!");
							// Tournament can be started
							tournamentRunning = true;
							for (UUID uuid : tournamentMMQ)
							{
								tournamentParticipants.add(uuid);
								tournamentScore.put(uuid, 0);
							}
							
							for (Player player : Bukkit.getOnlinePlayers())
							{
								if (tournamentParticipants.contains(player.getUniqueId()))
								{
									preTournamentLocation.put(player.getUniqueId(), player.getLocation());
									player.setHealth(20);
									player.setFoodLevel(20);
									player.teleport(spectatorLobby);
								}
							}
							
							tournamentFighting.add(tournamentParticipants.get(0));
							tournamentFighting.add(tournamentParticipants.get(1));
							tournamentParticipants.remove(tournamentParticipants.get(1));
							tournamentParticipants.remove(tournamentParticipants.get(0));
							for (Player player : Bukkit.getOnlinePlayers())
							{
								if (tournamentFighting.get(0) == player.getUniqueId())
								{
									player.teleport(posistionOne);
								}
								if (tournamentFighting.get(1) == player.getUniqueId())
								{
									player.teleport(posistionTwo);
								}
							}
							
							} else
							{
								Bukkit.broadcastMessage("Position 2 not set for thunderdome tournament");
							}
						} else
						{
							Bukkit.broadcastMessage("Posistion 1 not set for thunderdome tournament!");
						}
					} else
					{
					Bukkit.broadcastMessage("Spectator lobby not set for thunderdome tournament!");
					}
				}
			}
		}, 0L, 600L);
	}
	
	// Constructor
	public Main()
	{
		prizes.add(new ItemStack(Material.GOLD_BLOCK));
	}
	
	
	public boolean onCommand(CommandSender sender, Command cmd, String command, String[] args)
	{
		if (!(sender instanceof Player)) { return true; } // Literally stops non players from using these commands
		
		Player player = (Player) sender;
		
		if (command.equalsIgnoreCase("thunderhelp"))
		{
			player.sendMessage("---Thunder Help---");
			player.sendMessage("Duel a target");
			player.sendMessage("/thunderchallenge [playerName]");
			player.sendMessage("Queue for the next thunderdome tournament to win random prizes");
			player.sendMessage("/thunderqueue");
			player.sendMessage("See how many people are queued to compete in the next tournament");
			player.sendMessage("/thunderqueued");
			if (player.isOp())
			{
				player.sendMessage("---Admin Help---");
				player.sendMessage("Start or stop thuderdome event by running below command");
				player.sendMessage("/thunderstart");
				player.sendMessage("Set thunder dome spectator location");
				player.sendMessage("/thundersetspectatorlobby");
				player.sendMessage("Set player arena posistion 1");
				player.sendMessage("/thundersetpos1");
				player.sendMessage("Set player arena posistion 2");
				player.sendMessage("/thundersetpos2");
			}
			return true;
		}
		
		if (command.equalsIgnoreCase("thunderchallenge")) 
		{
			player.sendMessage("This command is not available... yet :V");
			player.sendMessage("You will be able to duel people for their xp, items, echest items, etc they have to agree though :v");
			return true;
		}
		
		if (command.equalsIgnoreCase("thunderqueue")) 
		{
			if (!tournamentMMQ.contains(player.getUniqueId()))
			{
				tournamentMMQ.add(player.getUniqueId());
				player.sendMessage("Addedd you to the tournament queue");
				return true;
			} else
			{
				tournamentMMQ.remove(player.getUniqueId());
				player.sendMessage("Removed you from the tournament queue");
				return true;
			}
		}
		
		if (command.equalsIgnoreCase("thunderqueued")) // Tells player how many more people are need for thunder dome to start
		{
			player.sendMessage("There are currently: " + tournamentMMQ.size() + " players competeting");
			player.sendMessage("The game needs more than: " + requiredMMQSize + " players queued to start");
			return true;
		}
		
		if (command.equalsIgnoreCase("thunderstart")) // Starts and ends server wide thunder dome event in main arena
		{
			if (player.isOp())
			{
				if (autoStart == false)
				{
					autoStart = true;
					Bukkit.broadcastMessage("server wide thunderdome event started by " + player.getName() + "!");
					return true;
				} else
				{
					autoStart = false;
					Bukkit.broadcastMessage("server wide thunderdome event ended by " + player.getName() + "!");
					return true;
				}
			}
			return true;
		}
		
		if (command.equalsIgnoreCase("thundersetspectatorlobby"))
		{
			if (player.isOp())
			{
				config.set("game.spectatorLobby.world", player.getWorld().getName());
				config.set("game.spectatorLobby.x", player.getLocation().getBlockX());
				config.set("game.spectatorLobby.y", player.getLocation().getBlockY());
				config.set("game.spectatorLobby.z", player.getLocation().getBlockZ());
				player.sendMessage("Set tournament spectator lobby at " + player.getWorld().getName() + " " + player.getLocation().getBlockX() + " " + player.getLocation().getBlockY() + " " + player.getLocation().getBlockZ());
				saveConfig();
				return true;
			} else
			{
				return true;
			}
		}
		
		if (command.equalsIgnoreCase("thundersetpos1"))
		{
			if (player.isOp())
			{
				config.set("game.position1.world", player.getWorld().getName());
				config.set("game.position1.x", player.getLocation().getBlockX());
				config.set("game.position1.y", player.getLocation().getBlockY());
				config.set("game.position1.z", player.getLocation().getBlockZ());
				player.sendMessage("Set tournament posistion 1 at " + player.getWorld().getName() + " " + player.getLocation().getBlockX() + " " + player.getLocation().getBlockY() + " " + player.getLocation().getBlockZ());
				saveConfig();
				return true;
			} else
			{
				return true;
			}
		}
		
		if (command.equalsIgnoreCase("thundersetpos2"))
		{
			if (player.isOp())
			{
				config.set("game.position2.world", player.getWorld().getName());
				config.set("game.position2.x", player.getLocation().getBlockX());
				config.set("game.position2.y", player.getLocation().getBlockY());
				config.set("game.position2.z", player.getLocation().getBlockZ());
				
				player.sendMessage("Set tournament posistion 2 at " + player.getWorld().getName() + " " + player.getLocation().getBlockX() + " " + player.getLocation().getBlockY() + " " + player.getLocation().getBlockZ());
				saveConfig();
				return true;
			} else
			{
				return true;
			}
		}
		
		return true;
	}
}
