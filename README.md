# ShowOff *(by LunaPixu)*
ShowOff is Yukkuricraft's implementation of a feature to showcase your items in Minecraft chat. If you've ever played Terraria, this multiplayer feature might be familiar.

ShowOff is written in Scala and built with SBT for Paper 1.21.4. It also supports Discord integration via [DiscordSRV](https://docs.discordsrv.com/).

Chat and Discord messages can be readily customised in the plugin's config file. In-game chat messages and text utilise the [MiniMessage](https://docs.papermc.io/adventure/minimessage/format/) text format to allow for flexible and rich message customisation.

## Commands
* `/showoff showitem` *or* `/showitem` - Showcase the item in your hand in chat (and on Discord if DiscordSRV is installed and enabled in the config).
  * `/showitem <slot-number>` - Showcase the item in that inventory slot number (1 - 9 for hotbar, 10 - 36 for inventory, 37 - 41 for equipment slots).
  * `/showitem hotbar <slot-number>` - Alternate command for showing hotbar slots, see above.
  * `/showitem inventory <slot-number>` - Alternate command for showing inventory slots (1 - 27).
  * `/showitem hand|offhand|head|chest|legs|feet` - Shortcuts for showing equipment slots.
* `/showoff reload` - Reload the plugin's config file and update message formatting.

## Permissions
* `showoff.showitem` - Grants access to the `/showitem` command to show off items.
* `showoff.reload` - Allows you to use `/showoff reload` to reload the plugin's config file.

## Discord
To use ShowOff's Discord integration, [DiscordSRV](https://docs.discordsrv.com/) must be installed onto your Minecraft and Discord servers.

ShowOff can be configured to send shown items to a Discord text channel of your choosing. If the choice is commented out in the config (as it is by default), the plugin will use your main/global channel to send messages.

## Config

| Config name                                 | Default                                                    | Description / Notes |
| ------------------------------------------- | ---------------------------------------------------------- | ------------------- |
| `commands.showitem.always-use-plural`       | `false`                                                    | When enabled, chat messages will always use the plural variant regardless of quantity or stack size. |
| `commands.showitem.message`                 | *see variants below*                                       | [MiniMessage](https://docs.papermc.io/adventure/minimessage/format/) format strings for how the show off item message will look in-game. Standard MiniMessage tags available up to v4.20 will work. In addition to the standard tags, `<player>`, `<item>`, and `<quantity>` are custom tags used to represent the player, the shown item, and its quantity respectively. |
| ...`.single`                                | `"<player> <yellow>shows off their [<item>]!"`             | While absent by default, the `<quantity>` tag may be used here if desired. |
| ...`.plural`                                | `"<player> <yellow>shows off their <quantity>x [<item>]!"` | |
| `commands.showitem.show-original-name`      | `true`                                                     | Appends an item's original name onto its hovertext (and Discord message) if renamed. If the `minecraft:item_name` component has been set, this name will take priority over the vanilla item name. |
| `commands.showitem.original-name-hovertext` | `"<dark_gray><italic:false>[Original name: <name>]"`       | MiniMessage format for how the "Original name" blurb on shown items will appear. The tag `<name>` represents the original name as it would have displayed in-game. |
| `commands.showitem.send-to-discord`         | `true`                                                     | **Requires [DiscordSRV](https://docs.discordsrv.com/)!** Sends shown items to your server's Discord. |
| `commands.showitem.discord-channel`         | *commented out*                                            | If commented out, the plugin will use your DiscordSRV's main/global channel. Otherwise, it will use the first channel it can find that matches the name entered. (Name is case-sensitive!) |
| `commands.showitem.discord-message`         | *see variants below*                                       | Hybrid MiniMessage/Discord format strings for how the shown item message will look on Discord. Standard MiniMessage format/decoration tags like `<italic>` will not work. Instead, Discord markdown formatting is used. |
| ...`.single`                                | `"**<player>** shows off their **<item>**"`                | |
| ...`.plural`                                | `"**<player>** shows off their *<quantity>x* **<item>**"`  | |
| `commands.showitem.use-embedded-message`    | `false`                                                    | If set `true`, the plain message defined above is wrapped into a Discord embed.
| `commands.showitem.embed-color`             | `"#ffff00"`                                                | The color of the embed message's frame. |