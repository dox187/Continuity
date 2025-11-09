package me.pepperbell.continuity.client.resource;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Properties;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

public final class EmissiveSuffixLoader {
	private static final Logger LOGGER = LoggerFactory.getLogger("Continuity/EmissiveSuffix");
	public static final Identifier LOCATION = Identifier.ofVanilla("optifine/emissive.properties");

	private static String emissiveSuffix;

	@Nullable
	public static String getEmissiveSuffix() {
		return emissiveSuffix;
	}

	public static void load(ResourceManager manager) {
		emissiveSuffix = null;

		Optional<Resource> optionalResource = manager.getResource(LOCATION);
		if (optionalResource.isPresent()) {
			Resource resource = optionalResource.get();
			try (InputStream inputStream = resource.getInputStream()) {
				Properties properties = new Properties();
				properties.load(inputStream);
				emissiveSuffix = properties.getProperty("suffix.emissive");

				if (emissiveSuffix != null) {
					LOGGER.info("[Continuity] Loaded emissive suffix: '{}'", emissiveSuffix);
				} else {
					LOGGER.warn("[Continuity] Property 'suffix.emissive' not found in {}",
							LOCATION);
				}
			} catch (IOException e) {
				LOGGER.error("[Continuity] Failed to load emissive suffix from '{}'", LOCATION, e);
			}
		}
	}
}
