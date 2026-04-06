# ShowOff *(by LunaPixu)*
ShowOff is Yukkuricraft's implementation of a feature to showcase your items in Minecraft chat. If you've ever played Terraria, this multiplayer feature might be familiar.

ShowOff is written in Scala and built with SBT for Paper 1.21.4. It also supports Discord integration via [DiscordSRV](https://docs.discordsrv.com/).

Chat and Discord messages can be readily customised in the plugin's config file. In-game chat messages and text utilise the [MiniMessage](https://docs.papermc.io/adventure/minimessage/format/) text format to allow for flexible and rich message customisation.

### Commands
* `/showoff showitem` *or* `/showitem` - Showcase your item in chat (and on Discord if DiscordSRV is installed).
* `/showoff reload` - Reload the plugin's config file and update message formatting.

### Permissions
* `showoff.showitem` - Grants access to the `/showitem` command to show off items
* `showoff.reload` - Allows you to use `/showoff reload` to reload the plugin's config file.

### Discord
To use ShowOff's Discord integration, [DiscordSRV](https://docs.discordsrv.com/) must be installed onto your Minecraft and Discord servers.

At present, ShowOff will send messages to your configured "main channel". I believe this is defined as the "global" or "general" channel in the DiscordSRV config file.  
*Support for alternate or context-sensitive Discord channels may be implemented in the future.*

### Config

| Config name                                 | Default | Description / Notes |
| ------------------------------------------- | ------- | ------------------ |
| `commands.showitem.always-use-plural`       | `false` | When enabled, chat messages will always use the plural variant regardless of quantity or stack size. |
| `commands.showitem.message`                 | *see variants below* | [MiniMessage](https://docs.papermc.io/adventure/minimessage/format/) format strings for how the show off item message will look in-game. Standard MiniMessage tags available up to v4.20 will work. In addition to the standard tags, `<player>`, `<item>`, and `<quantity>` are custom tags used to represent the player, the shown item, and its quantity respectively. |
| ...`.single`                                | `"<white><player> <yellow>shows off their [<item>]!"` | While absent by default, the `<quantity>` tag may be used here if desired. |
| ...`.plural`                                | `"<white><player> <yellow>shows off their <quantity>x [<item>]!"` | |
| `commands.showitem.show-original-name`      | `true` | Appends an item's original name onto its hovertext (and Discord message) if renamed. If the `minecraft:item_name` component has been set, this name will take priority over the vanilla item name. |
| `commands.showitem.original-name-hovertext` | `"<dark_gray><italic:false>[Original name: <name>]"` | MiniMessage format for how the "Original name" blurb on shown items will appear. The tag `<name>` represents the original name as it would have displayed in-game. |
| `commands.showitem.send-to-discord`         | `true` | **Requires [DiscordSRV](https://docs.discordsrv.com/)!** Sends shown items to your server's main Discord channel. |
| `commands.showitem.discord-message`         | *see variants below* | Hybrid MiniMessage/Discord format strings for how the shown item message will look on Discord. Standard MiniMessage format/decoration tags like `<italic>` will not work. Instead, Discord markdown formatting is used. |
| ...`.single`                                | `"**<player>** shows off their **<item>**"` | |
| ...`.plural`                                | `"**<player>** shows off their *<quantity>x* **<item>**"` | |