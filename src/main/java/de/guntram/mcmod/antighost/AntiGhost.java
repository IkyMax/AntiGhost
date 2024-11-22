package de.guntram.mcmod.antighost;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import de.guntram.mcmod.crowdintranslate.CrowdinTranslate;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_G;

public class AntiGhost implements ClientModInitializer {
    public static final String MOD_ID = "antighost";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID + ".properties");
    private static KeyBinding requestBlocksKey;
    private static int radius = 4;  // default radius

    @Override
    public void onInitializeClient() {
        loadConfig();
        final String category = "key.categories.antighost";
        requestBlocksKey = new KeyBinding("key.antighost.reveal", GLFW_KEY_G, category);
        CrowdinTranslate.downloadTranslations(MOD_ID);
        KeyBindingHelper.registerKeyBinding(requestBlocksKey);
        ClientTickEvents.END_CLIENT_TICK.register(e -> keyPressed());
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            // /ghost [radius]
            dispatcher.register(ClientCommandManager.literal("ghost")
                    .then(ClientCommandManager.argument("radius", IntegerArgumentType.integer(1))
                            .executes(c -> {
                                int customRadius = IntegerArgumentType.getInteger(c, "radius");
                                this.requestBlocks(customRadius);
                                return 0;
                            })
                    )
                    // with configured radius
                    .executes(c -> {
                        this.requestBlocks(radius);
                        return 0;
                    })
            );

            // /antighost setradius <radius>
            dispatcher.register(ClientCommandManager.literal("antighost")
                    .then(ClientCommandManager.literal("setradius")
                            .then(ClientCommandManager.argument("radius", IntegerArgumentType.integer(1))
                                    .executes(c -> {
                                        radius = IntegerArgumentType.getInteger(c, "radius");
                                        saveConfig();
                                        ClientPlayerEntity player = MinecraftClient.getInstance().player;
                                        player.sendMessage(Text.translatable("msg.radius_set", radius), false);
                                        return 0;
                                    })
                            )
                    )
            );
        });
    }

    public void keyPressed() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (requestBlocksKey.wasPressed()) {
            this.requestBlocks(radius);
            player.sendMessage(Text.translatable("msg.request"), false);
        }
    }

    public void requestBlocks(int radius) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayNetworkHandler conn = mc.getNetworkHandler();
        if (conn == null) return;
        BlockPos pos = mc.player.getBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    PlayerActionC2SPacket packet = new PlayerActionC2SPacket(
                            PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK,
                            new BlockPos(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz),
                            Direction.UP       // with ABORT_DESTROY_BLOCK, this value is unused
                    );
                    conn.sendPacket(packet);
                }
            }
        }
    }

    private void loadConfig() {
        Properties properties = new Properties();

        if (Files.exists(CONFIG_PATH)) {
            try (var reader = Files.newBufferedReader(CONFIG_PATH)) {
                properties.load(reader);
                radius = Integer.parseInt(properties.getProperty("radius", String.valueOf(radius)));
            } catch (IOException | NumberFormatException e) {
                LOGGER.error("Failed to load configuration. Using default radius.", e);
            }
        } else {
            saveConfig();
        }
    }

    private void saveConfig() {
        Properties properties = new Properties();
        properties.setProperty("radius", String.valueOf(radius));

        try (var writer = Files.newBufferedWriter(CONFIG_PATH)) {
            Files.createDirectories(CONFIG_PATH.getParent());
            properties.store(writer, null);
        } catch (IOException e) {
            LOGGER.error("Failed to save configuration.", e);
        }
    }
}
