package net.lunapixu.showoff

import net.kyori.adventure.text.Component
import org.bukkit.entity.Entity
import org.bukkit.event.{Cancellable, Event, HandlerList}
import org.bukkit.inventory.{InventoryHolder, ItemStack}

object ShownItemEvent:
  private final var handlerList: HandlerList = HandlerList()
  def getHandlerList: HandlerList = handlerList

class ShownItemEvent(holder: Entity & InventoryHolder, item: ItemStack, message: Component)
  extends Event, Cancellable:
  private var cancelled: Boolean = false
  private var mutMessage = message

  def getHolder: Entity & InventoryHolder = holder
  def getItem: ItemStack = item
  def getMessage: Component = mutMessage

  def setMessage(messageComp: Component): Unit = { mutMessage = messageComp }

  override def getHandlers(): HandlerList = ShownItemEvent.handlerList
  override def isCancelled = cancelled
  override def setCancelled(cancel: Boolean): Unit = { cancelled = cancel }
