package net.lunapixu.showoff

import io.papermc.paper.command.brigadier.{BasicCommand, CommandSourceStack, Commands}
import net.kyori.adventure.text.*
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.{EntityType, Player}
import org.bukkit.Bukkit
import net.kyori.adventure.text.event.HoverEvent

class ShowItemCommand extends BasicCommand:
  override def execute(commandSourceStack: CommandSourceStack, args: Array[String]): Unit =
    val executor = commandSourceStack.getExecutor()
    if (executor.getType() != EntityType.PLAYER) then
      commandSourceStack.getSender().sendMessage("Only players can show off items!")
      return

    val msgColor = NamedTextColor.YELLOW

    val player = executor.asInstanceOf[Player]
    val item = player.getInventory().getItemInMainHand()
    val showOff = Component.text(player.getDisplayName(), msgColor)
      .append(Component.text(" shows off their ["))
      .append(item.effectiveName().hoverEvent(item.asHoverEvent()))
      .append(Component.text("]!", msgColor)).hoverEvent(null)

    Bukkit.getServer().sendMessage(showOff)