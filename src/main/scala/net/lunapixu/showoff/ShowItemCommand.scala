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
  val msgColor = NamedTextColor.YELLOW

  private def createCommand(commandName: String): LiteralArgumentBuilder[CommandSourceStack] = 
    return Commands.literal(commandName)
      .executes(ctx => showEveryone(ctx))

  def buildCommand(commandName: String): LiteralCommandNode[CommandSourceStack] = createCommand(commandName).build()

  private def createComponentFromString(rawString: String, componentParts: Component*): Option[Component] =
    val regex = "(.*?)(?<token>%\\d+\\$s)((?:.(?!%\\d+\\$s))*)" // I hate this so much...
    val parsePattern = Pattern.compile(regex)

    val matcher = parsePattern.matcher(rawString)
    if (matcher.results().count() < componentParts.length) then return None
    matcher.reset()

    val componentBuilder: TextComponent.Builder = matcher.results().reduce(Component.text(), (comp: TextComponent.Builder, result: MatchResult) => {
      if (!result.group(1).isEmpty()) then comp.append(Component.text(result.group(1), msgColor))

      val tokenNum = Integer.parseInt(result.group("token").substring(1, result.group("token").length() - 2))
      comp.append(componentParts(tokenNum - 1))

      if (result.group(3).isEmpty()) then 
        comp
      else
        comp.append(Component.text(result.group(3), msgColor))
    }, (fullComp: TextComponent.Builder, part: TextComponent.Builder) => fullComp.append(part))

    Some(componentBuilder.build().compact())

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
      
      sender.sendMessage(Component.text(emptyMsg, msgColor))
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

    val componentParts = ArrayBuffer(playerComponent(executorPlayer), itemComponent(item))
    if (context.pluralItems) componentParts.append(Component.text(item.getAmount(), msgColor))
    
    createComponentFromString(everyoneMessage, (componentParts.toArray)*) match
      case Some(component) => Bukkit.getServer().sendMessage(component)
      case None => {
        plugin.getLogger().severe(s"Config value at $configLoc is malformed. Either fix the config.yml file or delete it to generate a fresh one.")
        sender.sendMessage(Component.text("Error sending message! Please inform a server admin to fix the ShowOff config.yml file.", NamedTextColor.RED))
        return 0
      }

    return 1