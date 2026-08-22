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
import org.spongepowered.asm.mixin.injection.Inject;
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
     * Loads Sodium shader sources from the active Minecraft ResourceManager instead of
     * directly from the Sodium classpath resources.
     *
     * @author LinusDev
     * @reason Allow resource packs to override Sodium core shaders.
     */
    @Overwrite
    public static String getShaderSource(Identifier name) {
        if (shaders == null) {
            LOG.warn("Trying to load shaders, but shaders variable is not yet initialised; falling back to Sodium's classpath loader");
            String path = String.format("/assets/%s/shaders/%s", name.getNamespace(), name.getPath());

            try (InputStream in = ShaderLoader.class.getResourceAsStream(path)) {
                if (in == null) {
                    throw new RuntimeException("Shader not found: " + path);
                }
                return IOUtils.toString(in, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException("Failed to read shader source for " + path, e);
            }
        }

        var namespace = shaders.get(name.getNamespace());
        if (namespace == null) {
            throw new RuntimeException("No shaders available for namespace '" + name.getNamespace() + "'");
        }

        var shaderResource = namespace.get(name.getPath());
        if (shaderResource == null) {
            throw new RuntimeException("No shader found in namespace '" + name.getNamespace()
                    + "' for shader '" + name.getPath() + "'");
        }

        try {
            LOG.info("Loaded shader '{}:{}' from pack '{}'.", name.getNamespace(), name.getPath(),
                    shaderResource.source().location().title().getString());
            return IOUtils.toString(shaderResource.open(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Exception while reading shader source in namespace '" + name.getNamespace()
                    + "' for shader '" + name.getPath() + "'", e);
        }
    }
}
