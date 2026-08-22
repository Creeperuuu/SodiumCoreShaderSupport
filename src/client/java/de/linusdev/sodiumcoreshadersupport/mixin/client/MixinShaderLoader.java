package de.linusdev.sodiumcoreshadersupport.mixin.client;

import net.caffeinemc.mods.sodium.client.gl.shader.ShaderLoader;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import org.apache.commons.io.IOUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static de.linusdev.sodiumcoreshadersupport.Constants.LOG;
import static de.linusdev.sodiumcoreshadersupport.SodiumCoreShaderSupportClient.shaders;

@Mixin(ShaderLoader.class)
public class MixinShaderLoader {

    /**
     * Loads a shader from the active resource packs first. If the shader was not
     * overridden by a resource pack, load Sodium's original shader directly from
     * the Sodium mod container.
     *
     * This is important for Sodium includes such as sodium:include/chunk_matrices.glsl:
     * Sodium's shader resources are not guaranteed to be exposed through the normal
     * Minecraft ResourceManager, so falling back through FabricLoader is more reliable
     * than ShaderLoader.class.getResourceAsStream().
     *
     * @author LinusDev
     * @reason Allow resource packs to override Sodium core shaders while preserving
     *         Sodium's bundled shader includes.
     */
    @Overwrite
    public static String getShaderSource(Identifier name) {
        // First use the highest-priority resource supplied by the active resource packs.
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

        // Resource packs do not contain this shader. Read Sodium's bundled copy
        // directly from the Sodium mod container instead of relying on the context
        // classloader, which may not expose another mod's jar resources.
        if ("sodium".equals(name.getNamespace())) {
            Optional<Path> sodiumPath = FabricLoader.getInstance()
                    .getModContainer("sodium")
                    .flatMap(container -> container.findPath("assets/sodium/shaders/" + name.getPath()));

            if (sodiumPath.isPresent()) {
                Path path = sodiumPath.get();
                try (InputStream in = Files.newInputStream(path)) {
                    LOG.info("Loaded shader '{}:{}' from Sodium's bundled resources.",
                            name.getNamespace(), name.getPath());
                    return IOUtils.toString(in, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to read Sodium shader source from " + path, e);
                }
            }
        }

        // Keep the original classpath fallback for non-Sodium namespaces and for
        // environments where Sodium's resource can still be resolved normally.
        String resourcePath = String.format("/assets/%s/shaders/%s", name.getNamespace(), name.getPath());
        try (InputStream in = ShaderLoader.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new RuntimeException("Shader not found in resource packs or Sodium: " + name);
            }
            return IOUtils.toString(in, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read shader source for " + resourcePath, e);
        }
    }
}
