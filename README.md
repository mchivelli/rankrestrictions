# FTBRanks Rank Restrictions

An addon for FTB Ranks that enforces item, block, and armor restrictions based on player ranks.

## Features

- **Restrict Items By Rank**: Prevent players from using items they shouldn't have access to
- **Restrict Block Entities By Rank**: Prevent players from interacting with restricted block entities (furnaces, modded machines, etc.)
- **Multiple Restriction Types**:
  - Individual item/block restrictions (e.g., `minecraft:diamond_sword`, `tconstruct:smeltery_controller`)
  - Mod-wide restrictions (e.g., `botania:*` to restrict all items/blocks from a mod)
  - Tag-based restrictions (e.g., `#minecraft:swords` to restrict all items with the sword tag)
- **In-game Commands**: Reload configuration without server restart
- **Real-time Enforcement**: Actively monitors player inventories, equipment, and block interactions

## Installation

1. Install [Minecraft Forge](https://files.minecraftforge.net/) for Minecraft 1.20.1
2. Install [FTB Ranks](https://www.curseforge.com/minecraft/mc-mods/ftb-ranks-forge) for Minecraft 1.20.1
3. Download the latest release of RankRestrictions
4. Place the JAR file in your server's `mods` folder
5. Start the server and configure the restriction settings

## Configuration

After first run, a configuration file will be created at `config/rankrestrictions.toml`. Edit this file to define restrictions:

```toml
# Example configuration
[restrictions.default]
  [[restriction_sets]]
    items = [
      "minecraft:diamond_sword",
      "minecraft:netherite_chestplate",
      "botania:*",
      "#minecraft:beds"
    ]
    blocks = [
      "minecraft:furnace",
      "tconstruct:smeltery_controller",
      "mekanism:*",
      "#minecraft:anvil"
    ]
    message = "&cYou cannot use %item% with your current rank!"
  
[restrictions.vip]
  [[restriction_sets]]
    items = [
      "minecraft:command_block",
      "minecraft:barrier"
    ]
    blocks = [
      "minecraft:command_block"
    ]
```

### Restriction Types

- **Specific item/block**: `"minecraft:diamond_sword"`, `"tconstruct:smeltery_controller"`
- **All items/blocks from a mod**: `"modid:*"`
- **Item/block tag**: `"#minecraft:beds"` (starts with `#`)

## Commands

- `/rankrestrictions reload` - Reloads the configuration file without restarting the server

## Technical Details

RankRestrictions uses reflection to interact with the FTB Ranks API, making it compatible with different versions without hard dependencies. The mod enforces restrictions through multiple mechanisms:

1. **Player Tick Events**: Checks player inventories every 30 seconds (600 ticks)
2. **Equipment Change Events**: Monitors equipment slots for restricted items
3. **Item Pickup Events**: Prevents picking up restricted items
4. **Item Usage Events**: Prevents using restricted items
5. **Block Interaction Events**: Prevents interacting with restricted block entities (furnaces, modded machines, etc.)

Restricted items are automatically removed from the player's inventory or equipment when detected. Block interactions are canceled and the player receives a message explaining the restriction.

## Dependencies

- Minecraft Forge 1.20.1
- FTB Ranks (1902.1.9 or newer for 1.20.1)

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
