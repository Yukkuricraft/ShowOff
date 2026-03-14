package net.lunapixu.showoff

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import org.bukkit.Bukkit
import org.bukkit.event.{EventHandler, Listener}
import org.bukkit.plugin.java.JavaPlugin
import net.lunapixu.showoff.ShowItemCommand
import io.papermc.paper.command.brigadier.BasicCommand

class ShowOff extends JavaPlugin:
  override def onEnable(): Unit =
    registerCommand("showitem", new ShowItemCommand())