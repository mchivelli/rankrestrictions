import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays; // For Arrays.asList

public class ConfigTest {
    public static void main(String[] args) {
        try {
            // Delete any existing config
            Path configDir = Paths.get("config/rankrestrictions");
            Path configFile = configDir.resolve("restrictions.toml");
            
            if (Files.exists(configFile)) {
                Files.delete(configFile);
                System.out.println("Deleted existing config file");
            }
            
            if (Files.exists(configDir)) {
                Files.delete(configDir);
                System.out.println("Deleted config directory");
            }
            
            // Create an instance of RankRestrictionsConfig to generate a new config
            mchivelli.rankrestrictions.config.RankRestrictionsConfig config = 
                new mchivelli.rankrestrictions.config.RankRestrictionsConfig();
            
            // Add test restrictions for 'member' rank, set index 0
            config.addOrUpdateRestriction(
                "member", 
                0, 
                Arrays.asList("minecraft:diamond_sword", "minecraft:netherite_chestplate", "#forge:tools,minecraft:beacon"), 
                "&cMembers cannot use %item%!"
            );

            // Add test restrictions for 'vip' rank, set index 0
            // This rank will use the default restriction message as no message is specified here for its set.
            config.addOrUpdateRestriction(
                "vip", 
                0, 
                Arrays.asList("minecraft:command_block", "minecraft:barrier"), 
                null // No custom message for this set, will use default
            );
            
            // Example of a second restriction set for the 'vip' rank
            config.addOrUpdateRestriction(
                "vip",
                1, // Set index 1
                Arrays.asList("minecraft:bedrock"),
                "&4VIPs absolutely cannot touch bedrock!"
            );
            
            // Save config
            config.saveConfig();
            
            // Print success
            System.out.println("Config file generated successfully");
            
            // Read and print the generated file
            if (Files.exists(configFile)) {
                System.out.println("\nGenerated config file contents:");
                System.out.println("-----------------------------------");
                String content = Files.readString(configFile);
                System.out.println(content);
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
