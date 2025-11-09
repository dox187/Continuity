package me.pepperbell.continuity.client.resource;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Properties;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import me.pepperbell.continuity.client.ContinuityClient;
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

		LOGGER.info("[Continuity] EMISSIVE SUFFIX LOADER:");
		LOGGER.info("  Looking for: {}", LOCATION);

		Optional<Resource> optionalResource = manager.getResource(LOCATION);
		if (optionalResource.isPresent()) {
			Resource resource = optionalResource.get();
			LOGGER.info("  Found emissive.properties file in pack: {}", resource.getPack().getId());
			try (InputStream inputStream = resource.getInputStream()) {
				Properties properties = new Properties();
				properties.load(inputStream);
				emissiveSuffix = properties.getProperty("suffix.emissive");

				if (emissiveSuffix != null) {
					LOGGER.info("  Emissive suffix loaded: '{}'", emissiveSuffix);
				} else {
					LOGGER.warn("  Property 'suffix.emissive' not found in file!");
				}
			} catch (IOException e) {
				LOGGER.error("Failed to load emissive suffix from file '" + LOCATION + "'", e);
				ContinuityClient.LOGGER
						.error("Failed to load emissive suffix from file '" + LOCATION + "'", e);
			}
		} else {
			LOGGER.warn("  File not found! Emissive textures will NOT work!");
			LOGGER.warn(
					"  To enable emissive textures, create: assets/minecraft/optifine/emissive.properties");
			LOGGER.warn("  With content: suffix.emissive=_e");
		}
	}
}
