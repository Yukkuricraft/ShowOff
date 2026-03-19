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
import java.util.regex.*
import java.io.IOException
import java.util.zip.DataFormatException
import scala.collection.mutable.ArrayBuffer

class Token(idNum: Int):
  val id = idNum

class ShowItemCommand(plugin: ShowOff):
  private def createCommand(commandName: String): LiteralArgumentBuilder[CommandSourceStack] = 
    return Commands.literal(commandName)
      .executes(ctx => showEveryone(ctx))

  def buildCommand(commandName: String): LiteralCommandNode[CommandSourceStack] = createCommand(commandName).build()

  private def parseTokens(rawString: String, expectedTokens: Int): Option[ArrayBuffer[String|Token]] =
    val regex = "(.*?)(?<token>%\\d+\\$s)((?:.(?!%\\d+\\$s))*)" // I hate this so much...
    val parsePattern = Pattern.compile(regex)

    val matcher = parsePattern.matcher(rawString)
    if (matcher.results().count() != expectedTokens) then return None
    matcher.reset()

    val results = ArrayBuffer[String|Token]()
    matcher.results().forEach((result) => {
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

    val player = executor.asInstanceOf[Player]
    val item = player.getInventory().getItemInMainHand()

    val plural = item.getAmount() > 1
    
    val config = plugin.config
    val configLoc = "commands.showitem.show-everyone-message" + (plural match
      case true => ".plural"
      case false => ".single")
    val malformedConfigErr = DataFormatException(s"Config value at $configLoc is malformed. Either fix the config.yml file or delete it to generate a fresh one.")

    val everyoneMessage = config match
      case Some(conf) => conf.getString(configLoc)
      case None => throw malformedConfigErr

    if (everyoneMessage == null) then
      throw IOException(s"Could not load config value at $configLoc")

    val tokenNum = plural match
      case true => 3
      case false => 2

    val tokenisedMessage = ArrayBuffer[String | Token]()
    parseTokens(everyoneMessage, tokenNum) match
      case Some(tokens) => tokenisedMessage.appendAll(tokens)
      case None => throw malformedConfigErr

    val msgColor = NamedTextColor.YELLOW

    var componentBuilder = Component.text()
    tokenisedMessage.foreach(subStr => subStr match
      case str: String => componentBuilder = componentBuilder.append(Component.text(str, msgColor))
      case token: Token => {
        token.id match
          case 1 => componentBuilder = componentBuilder.append(Component.text(player.getDisplayName(), NamedTextColor.WHITE))
          case 2 => componentBuilder = componentBuilder.append(item.effectiveName().hoverEvent(item.asHoverEvent()))
          case 3 => componentBuilder = componentBuilder.append(Component.text(item.getAmount(), msgColor))
          case _ => throw malformedConfigErr
      }
    )
    val showOff = componentBuilder.build().compact()

    Bukkit.getServer().sendMessage(showOff)
    return 1