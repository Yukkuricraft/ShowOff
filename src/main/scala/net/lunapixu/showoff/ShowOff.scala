package net.lunapixu.showoff

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import org.bukkit.event.{EventHandler, Listener}
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.configuration.file.FileConfiguration
import io.papermc.paper.command.brigadier.{CommandSourceStack, Commands}
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import org.bukkit.entity.Player

class ShowOff extends JavaPlugin:
  var config: Option[FileConfiguration] = None
  override def onEnable(): Unit =
    saveDefaultConfig()
    config = Some(getConfig())

    getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, commands => {
      commands.registrar().register(ShowItemCommand(this).buildCommand("showoff"))
      commands.registrar().register(reloadCommand("reload").build())
    })

  private def reloadCommand(commandName: String): LiteralArgumentBuilder[CommandSourceStack] =
    return Commands.literal(commandName).executes(ctx => {
      this.reloadConfig()
      config = Some(getConfig())

      ctx.getSource().getSender().sendMessage("ShowOff config reloaded!")
      if (ctx.getSource().getSender().isInstanceOf[Player]) then System.out.println("ShowOff config reloaded!")
      1
    })