import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

public class ConfigTester {
    public static void main(String[] args) throws IOException {
        Path configDir = Paths.get("config", "rankrestrictions");
        Path configFile = configDir.resolve("restrictions.toml");
        
        // Create directory if it doesn't exist
        if (!Files.exists(configDir)) {
            Files.createDirectories(configDir);
        }
        
        // Write the header directly to the file first
        StringBuilder header = new StringBuilder();
        header.append("# RankRestrictions Configuration\n");
        header.append("# Configure item, block and armor restrictions per rank\n");
        header.append("#\n");
        header.append("# Format:\n");
        header.append("# [restrictions.{rankId}]\n");
        header.append("#   restrictions = [ \"item1\", \"item2\", \"tag1,item3,item4\" ]  # List of restricted items/blocks/tags (comma-separated values are supported)\n");
        header.append("#   messageForRestrictionSet = \"Custom message for this rank\"  # Custom message when a player tries to use a restricted item\n");
        header.append("#\n");
        header.append("# Example:\n");
        header.append("# [restrictions.member]\n");
        header.append("#   restrictions = [ \"minecraft:diamond_sword\", \"minecraft:netherite_chestplate\", \"#forge:tools,minecraft:beacon\" ]\n");
        header.append("#   messageForRestrictionSet = \"&cYou need a higher rank to use %item%!\"\n");
        header.append("#\n");
        header.append("# [restrictions.vip]\n");
        header.append("#   restrictions = [ \"minecraft:command_block\", \"minecraft:barrier\" ]\n");
        header.append("#\n\n");
        
        // Write header to file
        Files.writeString(configFile, header.toString());
        
        // Create config object
        CommentedFileConfig config = CommentedFileConfig.builder(configFile)
                .sync()
                .autosave()
                .preserveInsertionOrder()
                .build();
                
        // Load the existing file with our header
        config.load();
        
        // Set default message
        config.set("messages.default_restriction", "&cYou are not allowed to use %item% with your current rank!");
        config.setComment("messages.default_restriction", "Message shown when a player tries to use a restricted item. Use %item% as a placeholder for the item name.");
        
        // Add example ranks
        String[] rankIds = {"member", "vip", "knight", "count", "baron", "admin"};
        
        // Add restrictions for each rank
        for (String rankId : rankIds) {
            // Path for restrictions
            String restrictionsPath = "restrictions." + rankId + ".restrictions";
            config.set(restrictionsPath, Arrays.asList("minecraft:diamond_sword", "#forge:ores"));
            
            // Custom message with rank-specific example
            String messagePath = "restrictions." + rankId + ".messageForRestrictionSet";
            config.set(messagePath, "&cRank " + rankId + " cannot use %item%! Upgrade your rank!");
            
            // Set comments
            config.setComment(restrictionsPath, "Restricted items and blocks for " + rankId + " rank");
            config.setComment(messagePath, "Custom message shown when a " + rankId + " player tries to use a restricted item");
        }
        
        // Save the config file
        config.save();
        System.out.println("Config file created at: " + configFile.toAbsolutePath());
        
        // Print the content of the file
        String content = Files.readString(configFile);
        System.out.println("\nConfig file content:\n");
        System.out.println(content);
    }
}
