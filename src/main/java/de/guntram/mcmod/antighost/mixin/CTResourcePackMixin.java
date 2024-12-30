package de.guntram.mcmod.antighost.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import de.guntram.mcmod.crowdintranslate.CTResourcePack;

import net.minecraft.util.Identifier;


@Mixin(CTResourcePack.class)
public class CTResourcePackMixin {
    @Overwrite
    private static Identifier fromPath(String path) {
        if (path.startsWith("assets/"))
            path = path.substring("assets/".length());
        String[] split = path.split("/", 2);
        return Identifier.of(split[0], split[1]);
    }
}