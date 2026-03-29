package net.lunapixu.showoff

import com.mojang.brigadier.Command
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.{CommandSourceStack, Commands}
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver
import org.bukkit.command.CommandSender

import net.kyori.adventure.text.*
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.minimessage.*
import net.kyori.adventure.text.minimessage.tag.Tag
import net.kyori.adventure.text.minimessage.tag.resolver.{Placeholder, TagResolver}
import org.bukkit.Bukkit
import org.bukkit.entity.{Player, Entity}
import org.bukkit.inventory.ItemStack

import java.io.IOException
import java.util.regex.*
import java.util.zip.DataFormatException
import scala.collection.mutable.ArrayBuffer

class ShowItemContext(ctx: CommandContext[CommandSourceStack]):
  val sender: CommandSender = ctx.getSource().getSender()
  val senderAsPlayer = sender.isInstanceOf[Player] match
    case true => Some(sender.asInstanceOf[Player])
    case false => None

  val executor: Entity = ctx.getSource().getExecutor()
  val executorAsPlayer: Option[Player] = executor.isInstanceOf[Player] match
    case true => Some(executor.asInstanceOf[Player])
    case false => None

  val targetPlayer: Option[Player] = try
    val target = ctx.getArgument("target", classOf[PlayerSelectorArgumentResolver]).resolve(ctx.getSource()).getFirst()
    Some(target)
  catch
    case e: Exception => None

  val item: Option[ItemStack] = executorAsPlayer match
    case Some(player) => Some(player.getInventory().getItemInMainHand())
    case None => None
  val pluralItems: Boolean = item match
    case Some(i) => i.getAmount() > 1
    case None => false

  val isSelfSent: Boolean = executorAsPlayer match
    case Some(player) => sender.isInstanceOf[Player] && (sender.asInstanceOf[Player].getUniqueId() == player.getUniqueId())
    case None => false
    
class ShowItemCommand(plugin: ShowOff):
  private def createCommand(commandName: String): LiteralArgumentBuilder[CommandSourceStack] = 
    return Commands.literal(commandName)
      .requires(source => source.getSender().hasPermission("showoff.showitem"))
      .executes(ctx => showEveryone(ctx))

  def buildCommand(commandName: String): LiteralCommandNode[CommandSourceStack] = createCommand(commandName).build()

  private def parseMiniMsg(miniMessage: String, player: String, item: Component, quantity: String): Component =
    val mm = MiniMessage.miniMessage()
    mm.deserialize(miniMessage, Placeholder.unparsed("player", player), Placeholder.component("item", item), Placeholder.unparsed("quantity", quantity))

  private def playerComponent(player: Player) = 
    Component.text(player.getDisplayName(), NamedTextColor.WHITE)

  private def itemComponent(item: ItemStack) =
    item.effectiveName().hoverEvent(item.asHoverEvent())

  private def showEveryone(ctx: CommandContext[CommandSourceStack]): Int =
    val context = ShowItemContext(ctx)
    val sender = context.sender
    if (context.executorAsPlayer.isEmpty) then
      sender.sendMessage(Component.text("Error: Only players can show off items!", NamedTextColor.RED))
      return 0
    
    val executorPlayer = context.executorAsPlayer.get
    val item = context.item.get

    if (item.isEmpty()) then
      val emptyMsg = context.isSelfSent match
        case true => "You aren't holding anything to show off!"
        case false =>  s"${executorPlayer.getDisplayName()} isn't holding anything to show off!"
      
      sender.sendMessage(Component.text(emptyMsg, NamedTextColor.YELLOW))
      return 0

    val config = plugin.config
    val configLoc = "commands.showitem.show-everyone-message" + (
      context.pluralItems match
        case true => ".plural"
        case false => ".single"
    )
    val everyoneMessage = config match
      case Some(conf) => conf.getString(configLoc)
      case None => null

    if (everyoneMessage == null) then
      plugin.getLogger().severe(s"Could not load config value at $configLoc")
      sender.sendMessage(Component.text("Error loading plugin config! Please inform a server admin.", NamedTextColor.RED))
      return 0

    val playerName = executorPlayer.getDisplayName()
    val itemComp = itemComponent(item)
    val quantityStr = item.getAmount().toString()
    
    val message = parseMiniMsg(everyoneMessage, playerName, itemComp, quantityStr)
    Bukkit.getServer().sendMessage(message)

    return 1