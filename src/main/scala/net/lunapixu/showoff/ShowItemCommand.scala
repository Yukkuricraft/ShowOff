package net.lunapixu.showoff

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.Command
import github.scarsz.discordsrv.util.DiscordUtil
import github.scarsz.discordsrv.DiscordSRV
import io.papermc.paper.command.brigadier.{CommandSourceStack, Commands}
import java.util.logging.Level
import java.util.regex.Pattern
import net.kyori.adventure.text.*
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.event.HoverEvent.ShowItem
import net.kyori.adventure.text.format.*
import net.kyori.adventure.text.minimessage.*
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.{Entity, Player}
import org.bukkit.inventory.ItemStack
import org.bukkit.Bukkit
import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters.*

private def stripStrTokens(string: String): String =
  val tokenRegex = "[&§][\\da-fk-or]" // hehe da fkor :)
  val stripPattern = Pattern.compile(tokenRegex, Pattern.CASE_INSENSITIVE)
  stripPattern.matcher(string).replaceAll("")

class ShowItemContext(ctx: CommandContext[CommandSourceStack], plugin: ShowOff):
  val sender: CommandSender = ctx.getSource.getSender
  val senderAsPlayer: Option[Player] = sender match
    case player: Player => Some(player)
    case _              => None
  lazy val senderName: String = sender match
    case player: Player =>
      val plainText = PlainTextComponentSerializer.plainText
      stripStrTokens(plainText.serialize(player.displayName))
    case _ => sender.getName

  val executor: Entity = ctx.getSource.getExecutor
  val executorAsPlayer: Option[Player] = executor match
    case player: Player => Some(player)
    case _              => None
  lazy val executorName: String = executor match
    case player: Player =>
      val plainText = PlainTextComponentSerializer.plainText
      stripStrTokens(plainText.serialize(player.displayName))
    case _ => executor.getName

  lazy val item: Option[ItemStack] = executorAsPlayer.flatMap(p =>
    val stack = p.getInventory.getItemInMainHand
    Option.unless(stack.isEmpty)(stack)
  )
  lazy val pluralItems: Boolean = item match
    case Some(i) => i.getAmount > 1 || plugin.getConfig()
        .getBoolean("commands.showitem.always-use-plural")
    case None => false
  lazy val originalItemName: Option[Component] = item.flatMap(i => Some(getOriginalItemName(i)))
  lazy val itemNameChanged: Boolean = (item, originalItemName) match
    case (Some(i), Some(originalName)) =>
      val mm = MiniMessage.miniMessage
      mm.serialize(i.effectiveName) != mm.serialize(originalName)
    case _ => false

  lazy val isSelfSent: Boolean = (executorAsPlayer, senderAsPlayer) match
    case (Some(executorPlayer), Some(senderPlayer)) => senderPlayer.getUniqueId ==
        executorPlayer.getUniqueId
    case _ => false

  val discordPluginAvailable: Boolean = Bukkit.getPluginManager.isPluginEnabled("DiscordSRV")

  private def getOriginalItemName(item: ItemStack): Component =
    val clone = item.asOne
    val itemMeta = clone.getItemMeta
    itemMeta.customName(null)
    clone.setItemMeta(itemMeta)
    clone.effectiveName

case class CommandFail(feedback: Component, consoleLog: Option[(log: String, level: Level)] = None)

class ShowItemCommand(plugin: ShowOff):
  def createCommand(commandName: String): LiteralArgumentBuilder[CommandSourceStack] = Commands
    .literal(commandName).requires(source => source.getSender.hasPermission("showoff.showitem"))
    .executes(ctx => showEveryone(ctx))

  private def parseMiniMsg(
    miniMessage: String,
    player: String,
    item: Component,
    quantity: String
  ): Component =
    val mm = MiniMessage.miniMessage
    mm.deserialize(
      miniMessage,
      Placeholder.unparsed("player", player),
      Placeholder.component("item", item),
      Placeholder.unparsed("quantity", quantity)
    )

  private def createItemComponent(item: ItemStack, originalName: Option[Component]): Component =
    val hover = originalName match
      case Some(name) => createPrependedHoverLore(item, name)
      case None       => item.asHoverEvent
    item.effectiveName.hoverEvent(hover)

  private def createPlainItemName(context: ShowItemContext, useOriginalName: Boolean): String =
    val plainText = PlainTextComponentSerializer.plainText
    val itemName = context.item match
      case Some(item) => stripStrTokens(plainText.serialize(item.effectiveName))
      case None       => "ERR: No Item Found!"
    val originalName = context.originalItemName match
      case Some(name) => stripStrTokens(plainText.serialize(name))
      case None       => "ERR: No Name Found!"

    itemName + (useOriginalName && context.itemNameChanged).match
      case true  => s" (${originalName})"
      case false => ""

  private def createPrependedHoverLore(
    item: ItemStack,
    originalName: Component
  ): HoverEvent[ShowItem] =
    val hoverText = plugin.getConfig.getString("commands.showitem.original-name-hovertext")
    /* If the hovertext config line was made empty/blank,
    stop prepending text and return the default hover event */
    if hoverText.trim == "" then return item.asHoverEvent

    val clone = ItemStack(item)
    val originalNameText = MiniMessage.miniMessage
      .deserialize(hoverText, Placeholder.component("name", originalName))
    val spacer = Component
      .text("---------", Style.style(NamedTextColor.DARK_GRAY, TextDecoration.BOLD))
      .decoration(TextDecoration.ITALIC, false)

    // TODO: Try to find a cleaner approach if possible
    val lore = List[Component](originalNameText).appendedAll(
      item.lore match
        case null => List[Component]()
        case _    => ListBuffer[Component](spacer).addAll(item.lore.asScala)
    )
    clone.lore(lore.asJava)
    clone.asHoverEvent

  private def getEmptyMessage(context: ShowItemContext): Component =
    val emptyMsg = context.isSelfSent match
      case true  => "You aren't holding anything to show off!"
      case false => s"${context.executorName} isn't holding anything to show off!"
    Component.text(emptyMsg, NamedTextColor.YELLOW)

  private def tryGetShowMessageFormat(
    config: FileConfiguration,
    plural: Boolean
  ): Either[CommandFail, String] =
    val configLoc = "commands.showitem.message" + plural.match
      case true  => ".plural"
      case false => ".single"
    /* The returned Either obj would've been in the for comprehension of ShowEveryone,
    however the configLoc string only exists here */
    Option(config.getString(configLoc)).toRight(CommandFail(
      Component
        .text("Error loading plugin config! Please inform a server admin.", NamedTextColor.RED),
      Some(s"Could not load config value at $configLoc", Level.SEVERE)
    ))

  private def showEveryone(ctx: CommandContext[CommandSourceStack]): Int =
    val context = ShowItemContext(ctx, plugin)
    val config = plugin.getConfig()

    val commandOutput: Either[CommandFail, Component] =
      for
        // TODO: Consider changing this part to allow non-players to show items as well
        _ <- context.executorAsPlayer.toRight(CommandFail(
          Component.text("Error: Only players can show off items!", NamedTextColor.RED)
        ))
        item <- context.item.toRight(CommandFail(getEmptyMessage(context)))
        showMessageFormat <- tryGetShowMessageFormat(config, context.pluralItems)
      yield
        val playerName = context.executorName
        val useOriginalItemName = config.getBoolean("commands.showitem.show-original-name") &&
          context.itemNameChanged
        val itemComp =
          createItemComponent(item, context.originalItemName.filter(_ => useOriginalItemName))
        val quantityStr = item.getAmount.toString

        parseMiniMsg(showMessageFormat, playerName, itemComp, quantityStr)

    commandOutput match
      case Right(message) =>
        Bukkit.getServer.sendMessage(message)
        if context.discordPluginAvailable && config.getBoolean("commands.showitem.send-to-discord")
        then broadcastItemToDiscord(context)
        1
      case Left(fail) =>
        context.sender.sendMessage(fail.feedback)
        fail.consoleLog.foreach(l => plugin.getLogger.log(l.level, l.log))
        0

  private def broadcastItemToDiscord(context: ShowItemContext): Unit =
    if !context.discordPluginAvailable then return

    val discordPlugin = DiscordSRV.getPlugin
    // TODO: Allow server to decide which channel to send messages to
    val discordChannel = discordPlugin.getMainTextChannel
    if discordChannel == null then
      plugin.getLogger
        .warning("No Discord channel found. Could not relay show item message to Discord.")
      return
    val config = plugin.getConfig()

    val plainText = PlainTextComponentSerializer.plainText
    val playerName = DiscordUtil.escapeMarkdown(context.executorName)
    val itemName = DiscordUtil.escapeMarkdown(createPlainItemName(
      context,
      plugin.getConfig.getBoolean("commands.showitem.show-original-name")
    ))
    val quantity = context.item.get.getAmount.toString

    val messageLoc = "commands.showitem.discord-message" + context.pluralItems.match
      case true  => ".plural"
      case false => ".single"
    val messageFormat = config.getString(messageLoc)

    val populatedMessage = MiniMessage.miniMessage.deserialize(
      messageFormat,
      Placeholder.unparsed("player", playerName),
      Placeholder.unparsed("item", itemName),
      Placeholder.unparsed("quantity", quantity)
    )

    // TODO: Allow for message card support
    DiscordUtil.queueMessage(discordChannel, plainText.serialize(populatedMessage))
