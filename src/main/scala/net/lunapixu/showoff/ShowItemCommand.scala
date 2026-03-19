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
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import java.util.regex.*
import java.io.IOException
import java.util.zip.DataFormatException
import scala.collection.mutable.ArrayBuffer

class Token(idNum: Int):
  val id = idNum

class ShowItemCommand(plugin: JavaPlugin):
  private def createCommand(commandName: String): LiteralArgumentBuilder[CommandSourceStack] = 
    return Commands.literal(commandName)
      .executes(ctx => showEveryone(ctx))

  def buildCommand(commandName: String): LiteralCommandNode[CommandSourceStack] = createCommand(commandName).build()

  private def parseTokens(rawString: String, expectedTokens: Int): Option[ArrayBuffer[String|Token]] =
    val regex = "(.*?)(?<token>%\\d+\\$s)((?:.(?!%\\d+\\$s))*)" // I hate this so much...
    val parsePattern = Pattern.compile(regex)
    val matcher = parsePattern.matcher(rawString)

    if (!matcher.matches()) then return None
    val matches = matcher.results()
    if (matches.count() != expectedTokens) then return None

    val results = ArrayBuffer[String|Token]()
    matches.forEach((result) => {
      if (!result.group(1).isEmpty()) then results.append(result.group(1))
      results.append(
        Token(Integer.parseInt(
          result.group("token").substring(1, result.group("token").length() - 2)
        ))
      )
      if (!result.group(3).isEmpty()) then results.append(result.group(3))
    })
    Some(results)

  private def showEveryone(ctx: CommandContext[CommandSourceStack]): Int =
    val executor = ctx.getSource().getExecutor()
    if !(executor.isInstanceOf[Player]) then
      ctx.getSource().getSender().sendMessage(Component.text("Error: Only players can show off items!", NamedTextColor.RED))
      return 0
    
    val config = plugin.getConfig()
    val configLoc = "commands.showitem.show-everyone-message"
    val malformedConfigErr = DataFormatException(s"Config value at $configLoc is malformed. Either fix the config.yml file or delete it to generate a fresh one.")

    val everyoneMessage = config.getString(configLoc)
    if (everyoneMessage == null) then
      throw IOException(s"Could not load config value at $configLoc")

    val tokenisedMessage = ArrayBuffer[String | Token]()
    parseTokens(everyoneMessage, 3) match
      case Some(tokens) => tokenisedMessage.appendAll(tokens)
      case None => throw malformedConfigErr
    
    val player = executor.asInstanceOf[Player]
    val item = player.getInventory().getItemInMainHand()

    val msgColor = NamedTextColor.YELLOW
    val quantity = s"${item.getAmount()}x"

    val componentBuilder = Component.text("", msgColor)
    tokenisedMessage.foreach(subStr => subStr match
      case str: String => componentBuilder.append(Component.text(str, msgColor))
      case token: Token => {
        token.id match
          case 1 => componentBuilder.append(Component.text(player.getDisplayName(), NamedTextColor.WHITE))
          case 2 => componentBuilder.append(Component.text(quantity, msgColor))
          case 3 => componentBuilder.append(item.effectiveName().hoverEvent(item.asHoverEvent()))
          case _ => throw malformedConfigErr
      }
    )
    val showOff = componentBuilder.compact()

    Bukkit.getServer().sendMessage(showOff)
    return 1