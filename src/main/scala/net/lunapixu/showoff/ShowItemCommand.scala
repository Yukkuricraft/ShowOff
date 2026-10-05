package net.lunapixu.showoff

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.Command
import github.scarsz.discordsrv.dependencies.jda.api.entities.{EmbedType, MessageEmbed, TextChannel}
import github.scarsz.discordsrv.dependencies.jda.api.entities.MessageEmbed.AuthorInfo
import github.scarsz.discordsrv.dependencies.jda.api.MessageBuilder
import github.scarsz.discordsrv.util.DiscordUtil
import github.scarsz.discordsrv.DiscordSRV
import io.papermc.paper.command.brigadier.{CommandSourceStack, Commands}
import java.awt.Color
import java.util.logging.Level
import java.util.regex.Pattern
import net.kyori.adventure.text.*
import net.kyori.adventure.text.event.{ClickEvent, HoverEvent}
import net.kyori.adventure.text.event.HoverEvent.ShowItem
import net.kyori.adventure.text.format.*
import net.kyori.adventure.text.minimessage.*
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.{Bukkit, Nameable}
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.{Entity, Player}
import org.bukkit.inventory.{Inventory, InventoryHolder, ItemStack}
import scala.jdk.CollectionConverters.*

private def stripStrTokens(string: String): String =
  val tokenRegex = "[&§][\\da-fk-or]" // hehe da fkor :)
  val stripPattern = Pattern.compile(tokenRegex, Pattern.CASE_INSENSITIVE)
  stripPattern.matcher(string).replaceAll("")

class ShowItemContext(ctx: CommandContext[CommandSourceStack], slot: Option[Int])(using
  config: FileConfiguration
):
  val sender: CommandSender = ctx.getSource.getSender
  val senderAsPlayer: Option[Player] = sender match
    case player: Player => Some(player)
    case _              => None
  lazy val senderName: Component = getSenderName(sender)
  lazy val plainSenderName: String = getPlainName(senderName)

  val executor: Entity = ctx.getSource.getExecutor
  // Currently unused outside of the item getter as non-player targets are out of scope for plugin rn
  val executorAsHolder: Option[Entity & InventoryHolder] = executor match
    case holder: InventoryHolder => Some(holder)
    case _                       => None
  val executorAsPlayer: Option[Player] = executor match
    case player: Player => Some(player)
    case _              => None
  lazy val executorName: Component = getSenderName(executor)
  lazy val plainExecutorName: String = getPlainName(executorName)

  lazy val item: Option[ItemStack] = executorAsHolder.flatMap { holder => getItem(holder, slot) }
  lazy val pluralItems: Boolean = item match
    case Some(i) => i.getAmount > 1 || config.getBoolean("commands.showitem.always-use-plural")
    case None    => false
  lazy val originalItemName: Option[Component] = item.flatMap { i => Some(getOriginalItemName(i)) }
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

  private def getSenderName(sender: CommandSender): Component = sender match
    case p: Player => p.displayName.hoverEvent(p.asHoverEvent)
        .clickEvent(ClickEvent.suggestCommand(s"/tell ${p.getName} "))
    case e: Entity => Option(e.customName).getOrElse(e.name).hoverEvent(e.asHoverEvent)
    case s: Any    => s.name

  private def getPlainName(name: Component): String =
    val plainText = PlainTextComponentSerializer.plainText
    stripStrTokens(plainText.serialize(name))

  private def getItem(holder: Entity & InventoryHolder, slot: Option[Int]): Option[ItemStack] =
    val stack = (holder, slot) match
      case (p: Player, None)    => Option(p.getInventory.getItemInMainHand)
      case (e: Any, Some(slot)) => Option(e.getInventory.getItem(slot))
      case (e: Any, None)       => Option(e.getInventory.getItem(0))
    stack.filterNot { _.isEmpty }

  private def getOriginalItemName(item: ItemStack): Component =
    val clone = item.asOne
    val itemMeta = clone.getItemMeta
    itemMeta.customName(null)
    clone.setItemMeta(itemMeta)
    clone.effectiveName

case class CommandFail(feedback: Component, consoleLog: Option[(log: String, level: Level)] = None)
type SlotAlias = (name: String, num: Option[Int])

class ShowItemCommand(plugin: ShowOff):
  private val slotNumName = "slot-number"
  private val altSlots: List[SlotAlias] = ("hand", None) :: ("offhand", Some(40)) ::
    ("head", Some(39)) :: ("chest", Some(38)) :: ("legs", Some(37)) :: ("feet", Some(36)) :: Nil

  private val slotArg = Commands.argument(
    slotNumName,
    IntegerArgumentType
      .integer(1, altSlots.foldLeft(0) { (high, curr) => high max curr.num.getOrElse(-1) } + 1)
  ).executes { ctx =>
    showEveryone(ctx, Some(IntegerArgumentType.getInteger(ctx, slotNumName) - 1))
  }
  private val hotbarArg = Commands.literal("hotbar")
    .`then`(Commands.argument(slotNumName, IntegerArgumentType.integer(1, 9)).executes { ctx =>
      showEveryone(ctx, Some(IntegerArgumentType.getInteger(ctx, slotNumName) - 1))
    })
  private val invArg = Commands.literal("inventory")
    .`then`(Commands.argument(slotNumName, IntegerArgumentType.integer(1, 27)).executes { ctx =>
      showEveryone(ctx, Some(IntegerArgumentType.getInteger(ctx, slotNumName) + 9 - 1))
    })

  def createCommand(commandName: String): LiteralArgumentBuilder[CommandSourceStack] =
    val baseCommand = Commands.literal(commandName)
      .requires { _.getSender.hasPermission("showoff.showitem") }.executes { ctx =>
        showEveryone(ctx, None)
      }.`then`(hotbarArg).`then`(slotArg).`then`(invArg)
    altSlots.foldLeft(baseCommand) { (command, slot) =>
      command.`then`(Commands.literal(slot.name).executes { ctx => showEveryone(ctx, slot.num) })
    }

  private def parseMiniMsg(
    miniMessage: String,
    player: Component,
    item: Component,
    quantity: String
  ): Component =
    val mm = MiniMessage.miniMessage
    mm.deserialize(
      miniMessage,
      Placeholder.component("player", player),
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

    val lore: List[Component] = originalNameText :: Option(item.lore).fold(List[Component]()) { l =>
      spacer :: l.asScala.toList
    }
    clone.lore(lore.asJava)
    clone.asHoverEvent

  // This will need a considerable revision for the lang update
  private def getEmptyMessage(context: ShowItemContext, targettingSlot: Boolean): Component =
    val msgColor = NamedTextColor.YELLOW
    val emptyMsg = context.isSelfSent match
      case true  => Component.text("You aren't ", msgColor)
      case false => context.executorName.append(Component.text(" isn't ", msgColor))
    val holdStr = targettingSlot match
      case true => "holding anything in that slot to show off!"
      case false => "holding anything to show off!"
    emptyMsg.append(Component.text(holdStr, msgColor))

  private def tryGetShowMessageFormat(plural: Boolean)(using
    config: FileConfiguration
  ): Either[CommandFail, String] =
    val configLoc = "commands.showitem.message" + plural.match
      case true  => ".plural"
      case false => ".single"
    /* The returned Either obj would've been in the for comprehension of ShowEveryone,
    however the configLoc string only exists here */
    Option(config.getString(configLoc)).toRight(CommandFail(
      Component
        .text("Error loading plugin config! Please inform a server admin.", NamedTextColor.RED),
      Some(s"Could not load config value at ${configLoc}", Level.SEVERE)
    ))

  private def showEveryone(ctx: CommandContext[CommandSourceStack], slot: Option[Int]): Int =
    given config: FileConfiguration = plugin.getConfig()
    val context = ShowItemContext(ctx, slot)

    val commandOutput: Either[CommandFail, Component] =
      for
        holder <- context.executorAsPlayer.toRight(CommandFail(
          Component.text("Error: Only players can show off items!", NamedTextColor.RED)
        ))
        item <- context.item.toRight(CommandFail(getEmptyMessage(context, slot.isDefined)))
        showMessageFormat <- tryGetShowMessageFormat(context.pluralItems)
      yield
        val playerName = context.executorName
        val useOriginalItemName = config.getBoolean("commands.showitem.show-original-name") &&
          context.itemNameChanged
        val originalName = context.originalItemName.filter { _ => useOriginalItemName }
        val itemComp = createItemComponent(item, originalName)
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
        fail.consoleLog.foreach { l => plugin.getLogger.log(l.level, l.log) }
        0

  private def broadcastItemToDiscord(context: ShowItemContext): Unit =
    given config: FileConfiguration = plugin.getConfig()
    val messageLoc = "commands.showitem.discord-message" + context.pluralItems.match
      case true  => ".plural"
      case false => ".single"

    val discordOutput: Either[(message: String, level: Level), Int] =
      for
        discordAvailable <- Option(context.discordPluginAvailable).filter { _.self }
          .toRight(("Error: Discord plugin not available!", Level.SEVERE))
        player <- context.executorAsPlayer
          .toRight(("No show-off player found. Cannot relay message.", Level.WARNING))
        discordChannel <- tryGetChannel().toRight(
          ("No Discord channel found. Could not relay show item message to Discord.", Level.WARNING)
        )
        messageFormat <- Option(config.getString(messageLoc))
          .toRight((s"Could not load config value at ${messageLoc}", Level.SEVERE))
        item <- context.item.toRight(("No item found. Cannot relay message.", Level.WARNING))
      yield
        val plainText = PlainTextComponentSerializer.plainText
        val executorName = DiscordUtil.escapeMarkdown(context.plainExecutorName)
        val itemName = DiscordUtil.escapeMarkdown(createPlainItemName(
          context,
          plugin.getConfig.getBoolean("commands.showitem.show-original-name")
        ))
        val quantity = item.getAmount.toString

        val message = MiniMessage.miniMessage.deserialize(
          messageFormat,
          Placeholder.unparsed("player", executorName),
          Placeholder.unparsed("item", itemName),
          Placeholder.unparsed("quantity", quantity)
        )
        val plainMessage = stripStrTokens(plainText.serialize(message))

        config.getBoolean("commands.showitem.use-embedded-message") match
          case true  => embedMessage(discordChannel, player, plainMessage)
          case false => DiscordUtil.queueMessage(discordChannel, plainMessage)
        1

    discordOutput match
      case Left(log) => plugin.getLogger.log(log.level, log.message)
      case Right(_)  => () // Main output is currently already handled

  private def tryGetChannel()(using config: FileConfiguration): Option[TextChannel] = Option(
    config.getString("commands.showitem.discord-channel")
  ).fold(Option(DiscordSRV.getPlugin.getMainTextChannel)) { str =>
    val channels = DiscordUtil.getJda.getTextChannelsByName(str, false)
    Option.unless(channels.isEmpty) { channels.get(0) }
  }

  private def embedMessage(channel: TextChannel, player: Player, message: String)(using
    config: FileConfiguration
  ): Unit =
    val playerHeadUrl = DiscordSRV.getAvatarUrl(player)

    val defaultColor = Color(255, 255, 0)
    val color = Option(config.getString("commands.showitem.embed-color"))
      .fold(defaultColor) { str =>
        try Color.decode(str)
        catch e => defaultColor
      }

    val embed = MessageEmbed(
      "",
      DiscordUtil.translateEmotes(message),
      "-# *Show off your items with* `/showitem`",
      EmbedType.RICH,
      null,
      color.getRGB,
      null,
      null,
      AuthorInfo(stripStrTokens(player.getDisplayName), "", playerHeadUrl, playerHeadUrl),
      null,
      null,
      null,
      null
    )
    DiscordUtil.queueMessage(channel, MessageBuilder(embed).build)
