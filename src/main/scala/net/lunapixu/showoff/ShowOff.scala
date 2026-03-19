package net.lunapixu.showoff

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import org.bukkit.event.{EventHandler, Listener}
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.configuration.file.FileConfiguration

class ShowOff extends JavaPlugin:
  override def onEnable(): Unit =
    saveDefaultConfig()

    getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, commands => {
      commands.registrar().register(ShowItemCommand(this).buildCommand("showoff"))
    })