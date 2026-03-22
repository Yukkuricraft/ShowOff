package net.lunapixu.showoff

import io.papermc.paper.command.brigadier.{CommandSourceStack, Commands}
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import com.mojang.brigadier.Command
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.tree.LiteralCommandNode
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.kyori.adventure.text.*
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.Bukkit
import java.util.regex.*
import java.io.IOException
import java.util.zip.DataFormatException
import scala.collection.mutable.ArrayBuffer

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
    val sender = ctx.getSource().getSender()
    val executor = ctx.getSource().getExecutor()
    if !(executor.isInstanceOf[Player]) then
      sender.sendMessage(Component.text("Error: Only players can show off items!", NamedTextColor.RED))
      return 0

    val player = executor.asInstanceOf[Player]
    val selfSent = sender.isInstanceOf[Player] && (sender.asInstanceOf[Player].getUniqueId() == player.getUniqueId())
    val item = player.getInventory().getItemInMainHand()

    if (item.isEmpty()) then
      val emptyMsg = selfSent match
        case true => "You aren't holding anything to show off!"
        case false =>  s"${player.getDisplayName()} isn't holding anything to show off!"
      
      sender.sendMessage(Component.text(emptyMsg, msgColor))
      return 0

    val plural = item.getAmount() > 1
    
    val config = plugin.config
    val configLoc = "commands.showitem.show-everyone-message" + (
      plural match
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

    val componentParts = ArrayBuffer(playerComponent(player), itemComponent(item))
    if (plural) componentParts.append(Component.text(item.getAmount(), msgColor))
    
    createComponentFromString(everyoneMessage, (componentParts.toArray)*) match
      case Some(component) => Bukkit.getServer().sendMessage(component)
      case None => {
        plugin.getLogger().severe(s"Config value at $configLoc is malformed. Either fix the config.yml file or delete it to generate a fresh one.")
        sender.sendMessage(Component.text("Error sending message! Please inform a server admin to fix the ShowOff config.yml file.", NamedTextColor.RED))
        return 0
      }
  
    return 1