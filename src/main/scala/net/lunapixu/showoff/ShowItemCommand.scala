package net.lunapixu.showoff

import io.papermc.paper.command.brigadier.{CommandSourceStack, Commands}
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import com.mojang.brigadier.Command
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.tree.LiteralCommandNode
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.kyori.adventure.text.*
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.{EntityType, Player}
import org.bukkit.Bukkit
import net.kyori.adventure.text.event.HoverEvent

class ShowItemCommand:
  private def createCommand(commandName: String): LiteralArgumentBuilder[CommandSourceStack] = 
    return Commands.literal(commandName)
      .executes(ctx => showEveryone(ctx))

  def buildCommand(commandName: String): LiteralCommandNode[CommandSourceStack] = createCommand(commandName).build()

  private def showEveryone(ctx: CommandContext[CommandSourceStack]): Int =
    val executor = ctx.getSource().getExecutor()
        if !(executor.isInstanceOf[Player]) then
          ctx.getSource().getSender().sendMessage(Component.text("Error: Only players can show off items!", NamedTextColor.RED))
          return 0

        val player = executor.asInstanceOf[Player]
        val item = player.getInventory().getItemInMainHand()

        // TODO: Make config to define output text instead of hardcoding strings
        val msgColor = NamedTextColor.YELLOW
        val quantity = if (item.getAmount() > 1) Component.text(s"${item.getAmount}x [") else Component.text("[")
        val showOff = Component.text(player.getDisplayName(), msgColor)
          .append(Component.text(" shows off their "))
          .append(quantity)
          .append(item.effectiveName().hoverEvent(item.asHoverEvent()))
          .append(Component.text("]!", msgColor)).hoverEvent(null)

        Bukkit.getServer().sendMessage(showOff)
        return 1