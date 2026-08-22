package de.linusdev.sodiumcoreshadersupport.mixin.client;

import net.caffeinemc.mods.sodium.client.gl.shader.GlShader;
import net.caffeinemc.mods.sodium.client.gl.shader.ShaderConstants;
import net.caffeinemc.mods.sodium.client.gl.shader.ShaderLoader;
import net.caffeinemc.mods.sodium.client.gl.shader.ShaderType;
import net.minecraft.resources.Identifier;
import org.apache.commons.io.IOUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static de.linusdev.sodiumcoreshadersupport.Constants.LOG;
import static de.linusdev.sodiumcoreshadersupport.SodiumCoreShaderSupportClient.shaders;

@Mixin(ShaderLoader.class)
public class MixinShaderLoader {

    @Inject(at = @At("HEAD"), method = "loadShader")
    private static void loadShaderInject(
            ShaderType type, Identifier name, ShaderConstants constants, CallbackInfoReturnable<GlShader> cir
    ) {
        LOG.info("Start loading shader in namespace '{}': {}", name.getNamespace(), name.getPath());
    }

    /**
     * Loads a shader from the active resource packs first, then falls back to Sodium's
     * bundled shader resource. This lets packs override only the shaders they provide
     * while imported Sodium includes continue to work normally.
     *
     * @author LinusDev
     * @reason Allow resource packs to override Sodium core shaders without breaking imports.
     */
    @Overwrite
    public static String getShaderSource(Identifier name) {
        if (shaders != null) {
            var namespace = shaders.get(name.getNamespace());
            if (namespace != null) {
                var shaderResource = namespace.get(name.getPath());
                if (shaderResource != null) {
                    try {
                        LOG.info("Loaded shader '{}:{}' from pack '{}'.", name.getNamespace(), name.getPath(),
                                shaderResource.source().location().title().getString());
                        return IOUtils.toString(shaderResource.open(), StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        throw new RuntimeException("Exception while reading shader source in namespace '"
                                + name.getNamespace() + "' for shader '" + name.getPath() + "'", e);
                    }
                }
            }
        }

        // The resource pack did not override this shader. Fall back to Sodium's bundled copy.
        String path = String.format("/assets/%s/shaders/%s", name.getNamespace(), name.getPath());
        try (InputStream in = ShaderLoader.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new RuntimeException("Shader not found in resource packs or Sodium: " + name);
            }
            LOG.info("Loaded shader '{}:{}' from Sodium's bundled resources.",
                    name.getNamespace(), name.getPath());
            return IOUtils.toString(in, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read shader source for " + path, e);
        }
    }
}
