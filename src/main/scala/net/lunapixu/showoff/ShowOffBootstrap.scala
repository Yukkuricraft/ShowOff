package net.lunapixu.showoff

import io.papermc.paper.plugin.bootstrap.PluginBootstrap
import io.papermc.paper.plugin.bootstrap.BootstrapContext
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import net.lunapixu.showoff.ShowItemCommand

class ShowOffBootstrap extends PluginBootstrap:
  override def bootstrap(context: BootstrapContext): Unit =
    context.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, commands => {
      commands.registrar().register(ShowItemCommand().buildCommand("showoff"))
    })
