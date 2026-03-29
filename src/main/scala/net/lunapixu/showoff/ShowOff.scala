package net.lunapixu.showoff

import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.plugin.java.JavaPlugin

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import org.bukkit.event.{EventHandler, Listener}

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import io.papermc.paper.command.brigadier.{CommandSourceStack, Commands}
import org.bukkit.entity.Player

class ShowOff extends JavaPlugin:
  var config: Option[FileConfiguration] = None
  override def onEnable(): Unit =
    saveDefaultConfig()
    config = Some(getConfig())

    getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, commands => {
      commands.registrar().register(ShowItemCommand(this).buildCommand("showitem"))
      commands.registrar().register(reloadCommand("reload").build())
    })

  def reloadCommand(commandName: String): LiteralArgumentBuilder[CommandSourceStack] =
    return Commands.literal(commandName)
      .requires(source => source.getSender().hasPermission("showoff.reload"))
      .executes(ctx => {
        this.reloadConfig()
        config = Some(getConfig())

        ctx.getSource().getSender().sendMessage("ShowOff config reloaded!")
        if (ctx.getSource().getSender().isInstanceOf[Player]) then this.getLogger().info("ShowOff config reloaded!")
        1
      })