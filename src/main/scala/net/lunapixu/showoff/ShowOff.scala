package net.lunapixu.showoff

import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.plugin.java.JavaPlugin

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import org.bukkit.event.{EventHandler, Listener}

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import io.papermc.paper.command.brigadier.{CommandSourceStack, Commands}
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player

class ShowOff extends JavaPlugin:
  override def onEnable(): Unit =
    saveDefaultConfig()

    getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, commands => {
      commands.registrar().register(ShowItemCommand(this).createCommand("showitem").build())
      commands.registrar().register(baseCommand("showoff").build())
    })

  def baseCommand(commandName: String): LiteralArgumentBuilder[CommandSourceStack] =
    Commands.literal(commandName)
      .`then`(reloadCommand("reload"))
      .`then`(ShowItemCommand(this).createCommand("showitem"))

  def reloadCommand(commandName: String) = 
    Commands.literal(commandName)
      .requires(source => source.getSender().hasPermission("showoff.reload"))
      .executes(ctx => {
        this.reloadConfig()

        val reloadMessage = "ShowOff config reloaded!"
        ctx.getSource().getSender().sendMessage(Component.text(reloadMessage, NamedTextColor.GOLD))
        
        if (ctx.getSource().getSender().isInstanceOf[Player]) then this.getLogger().info(reloadMessage)
        1
      }
    )