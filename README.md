# FTBRanks Rank Restrictions

An addon for FTB Ranks that enforces item, block, and armor restrictions based on player ranks.

## Features

- **Restrict Items By Rank**: Prevent players from using items they shouldn't have access to
- **Multiple Restriction Types**:
  - Individual item restrictions (e.g., `minecraft:diamond_sword`)
  - Mod-wide restrictions (e.g., `botania:*` to restrict all items from a mod)
  - Tag-based restrictions (e.g., `#minecraft:swords` to restrict all items with the sword tag)
- **In-game Commands**: Reload configuration without server restart
- **Real-time Enforcement**: Actively monitors player inventories and equipment

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
[ranks]
  [ranks.default]
    restricted_items = [
      "minecraft:diamond_sword",
      "minecraft:netherite_chestplate",
      "botania:*",
      "#minecraft:beds"
    ]
  
  [ranks.vip]
    restricted_items = [
      "minecraft:command_block",
      "minecraft:barrier"
    ]
```

### Restriction Types

- **Specific item**: `"minecraft:diamond_sword"`
- **All items from a mod**: `"modid:*"`
- **Item tag**: `"#minecraft:beds"` (starts with `#`)

## Commands

- `/rankrestrictions reload` - Reloads the configuration file without restarting the server

## Technical Details

RankRestrictions uses reflection to interact with the FTB Ranks API, making it compatible with different versions without hard dependencies. The mod enforces restrictions through two main mechanisms:

1. **Player Tick Events**: Checks player inventories every tick
2. **Equipment Change Events**: Monitors equipment slots for restricted items

Restricted items are automatically removed from the player's inventory or equipment when detected.

## Dependencies

- Minecraft Forge 1.20.1
- FTB Ranks (1902.1.9 or newer for 1.20.1)

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
