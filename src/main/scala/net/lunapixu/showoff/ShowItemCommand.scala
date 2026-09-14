package net.lunapixu.showoff

import com.mojang.brigadier.Command
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import io.papermc.paper.command.brigadier.{CommandSourceStack, Commands}
import org.bukkit.command.CommandSender

import net.kyori.adventure.text.*
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.event.HoverEvent.ShowItem
import net.kyori.adventure.text.format.*
import net.kyori.adventure.text.minimessage.*
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.entity.{Player, Entity}
import org.bukkit.inventory.ItemStack

import github.scarsz.discordsrv.DiscordSRV
import github.scarsz.discordsrv.util.{DiscordUtil, MessageUtil}

import scala.collection.mutable.{ArrayBuffer, ListBuffer}
import scala.jdk.CollectionConverters.*

class ShowItemContext(ctx: CommandContext[CommandSourceStack], plugin: ShowOff):
  val sender: CommandSender = ctx.getSource().getSender()
  val senderAsPlayer: Option[Player] = sender match
    case player: Player => Some(player)
    case _ => None
  lazy val senderName: String = sender match
    case player: Player =>
      val plainText = PlainTextComponentSerializer.plainText
      plainText.serialize(player.displayName)
    case _ => sender.getName()

  val executor: Entity = ctx.getSource().getExecutor()
  val executorAsPlayer: Option[Player] = executor match
    case player: Player => Some(player)
    case _ => None
  lazy val executorName: String = executor match
    case player: Player =>
      val plainText = PlainTextComponentSerializer.plainText
      plainText.serialize(player.displayName)
    case _ => executor.getName()

  val item: Option[ItemStack] = executorAsPlayer match
    case Some(player) => Some(player.getInventory().getItemInMainHand())
    case None => None
  lazy val pluralItems: Boolean = item match
    case Some(i) => i.getAmount() > 1 || plugin.getConfig().getBoolean("commands.showitem.always-use-plural")
    case None => false
  lazy val originalItemName: Option[Component] = item match
    case Some(i) => Some(getOriginalItemName(i))
    case None => None
  lazy val itemNameChanged: Boolean = (item.isDefined && originalItemName.isDefined) match
    case true =>
      val mm = MiniMessage.miniMessage
      mm.serialize(item.get.effectiveName()) != mm.serialize(originalItemName.get)
    case false => false

  lazy val isSelfSent: Boolean = executorAsPlayer match
    case Some(player) => sender.isInstanceOf[Player] && (sender.asInstanceOf[Player].getUniqueId() == player.getUniqueId())
    case None => false
    
  val discordPluginAvailable: Boolean = Bukkit.getPluginManager().isPluginEnabled("DiscordSRV")

  private def getOriginalItemName(item: ItemStack): Component = 
    val clone = item.asOne()
    val itemMeta = clone.getItemMeta()
    itemMeta.customName(null)
    clone.setItemMeta(itemMeta)
    clone.effectiveName()

class ShowItemCommand(plugin: ShowOff):
  def createCommand(commandName: String): LiteralArgumentBuilder[CommandSourceStack] = 
    return Commands.literal(commandName)
      .requires(source => source.getSender().hasPermission("showoff.showitem"))
      .executes(ctx => showEveryone(ctx))

  private def parseMiniMsg(miniMessage: String, player: String, item: Component, quantity: String): Component =
    val mm = MiniMessage.miniMessage()
    mm.deserialize(miniMessage, Placeholder.unparsed("player", player), Placeholder.component("item", item), Placeholder.unparsed("quantity", quantity))

  private def createItemComponent(item: ItemStack, originalName: Option[Component] = None) =
    val hover = originalName match
      case Some(name) => createPrependedHoverLore(item, name)
      case None => item.asHoverEvent()
    item.effectiveName().hoverEvent(hover)

  private def createPrependedHoverLore(item: ItemStack, originalName: Component): HoverEvent[ShowItem] =
    val hoverText = plugin.getConfig().getString("commands.showitem.original-name-hovertext")
    // If the hovertext config line was made empty/blank, stop prepending text and return the default hover event
    if (hoverText.trim() == "") then
      return item.asHoverEvent()

    val clone = ItemStack(item)
    val originalNameText = MiniMessage.miniMessage()
      .deserialize(hoverText, Placeholder.component("name", originalName))
    val spacer = Component.text("---------", Style.style(NamedTextColor.DARK_GRAY, TextDecoration.BOLD))
      .decoration(TextDecoration.ITALIC, false)
    
    // TODO: Cleanup this lore list builder
    val lore = List[Component](originalNameText).appendedAll(item.lore() match
      case null => List[Component]()
      case _ => ListBuffer[Component](spacer).addAll(item.lore().asScala)
    )
    clone.lore(lore.asJava)
    clone.asHoverEvent()

  private def showEveryone(ctx: CommandContext[CommandSourceStack]): Int =
    val context = ShowItemContext(ctx, plugin)
    val sender = context.sender
    if (context.executorAsPlayer.isEmpty) then
      sender.sendMessage(Component.text("Error: Only players can show off items!", NamedTextColor.RED))
      return 0
    
    if (context.item.isEmpty) then
      val emptyMsg = context.isSelfSent match
        case true => "You aren't holding anything to show off!"
        case false =>  s"${context.executorName} isn't holding anything to show off!"
      
      sender.sendMessage(Component.text(emptyMsg, NamedTextColor.YELLOW))
      return 0

    val item = context.item.get

    val config = plugin.getConfig()
    val configLoc = "commands.showitem.message" + ( context.pluralItems.match
      case true => ".plural"
      case false => ".single"
    )
    val everyoneMessage = config.getString(configLoc)

    if (everyoneMessage == null) then
      plugin.getLogger().severe(s"Could not load config value at $configLoc")
      sender.sendMessage(Component.text("Error loading plugin config! Please inform a server admin.", NamedTextColor.RED))
      return 0

    val playerName = context.executorName
    val itemComp = (config.getBoolean("commands.showitem.show-original-name") && context.itemNameChanged) match
      case true => createItemComponent(item, context.originalItemName)
      case false => createItemComponent(item)
    val quantityStr = item.getAmount().toString()
    
    val message = parseMiniMsg(everyoneMessage, playerName, itemComp, quantityStr)
    Bukkit.getServer().sendMessage(message)

    if (context.discordPluginAvailable && config.getBoolean("commands.showitem.send-to-discord")) then broadcastToDiscord(context)

    return 1

  private def broadcastToDiscord(context: ShowItemContext): Unit = 
    if (!context.discordPluginAvailable) return

    val discordPlugin = DiscordSRV.getPlugin()
    // TODO: Allow server to decide which channel to send messages to
    val discordChannel = discordPlugin.getMainTextChannel()
    if (discordChannel == null) then
      plugin.getLogger().warning("No Discord channel found. Could not relay show item message to Discord.")
      return
    val config = plugin.getConfig()

    val plainText = PlainTextComponentSerializer.plainText
    val playerName = DiscordUtil.escapeMarkdown(context.executorName)
    val itemName = DiscordUtil.escapeMarkdown(
      // I know how this works now, but the identation and readability is narsty
      (MessageUtil.strip(plainText.serialize(context.item.get.effectiveName))
      + ((config.getBoolean("commands.showitem.show-original-name") && context.itemNameChanged) match
        case true => s" (${MessageUtil.strip(plainText.serialize(context.originalItemName.get))})"
        case false => ""
      ))
    )
    val quantity = context.item.get.getAmount().toString()

    val messageLoc = "commands.showitem.discord-message" + (context.pluralItems match
      case true => ".plural"
      case false => ".single"
    )
    val messageFormat = config.getString(messageLoc)

    val populatedMessage = MiniMessage.miniMessage().deserialize(messageFormat,
      Placeholder.unparsed("player", playerName),
      Placeholder.unparsed("item", itemName),
      Placeholder.unparsed("quantity", quantity)
    )

    //TODO: Allow for message card support
    DiscordUtil.queueMessage(discordChannel, plainText.serialize(populatedMessage))