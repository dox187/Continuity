package me.pepperbell.continuity.client;

import me.pepperbell.continuity.client.resource.CtmInitializationCoordinator;
import me.pepperbell.continuity.client.resource.CtmResourceReloadListener;
import me.pepperbell.continuity.client.properties.CtmPropertiesLoader;
import net.fabricmc.fabric.api.resource.ResourceReloadListenerRegistry;
import net.minecraft.resource.ResourceManager;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import me.pepperbell.continuity.api.client.CtmProperties;

import me.pepperbell.continuity.api.client.CachingPredicates;
import me.pepperbell.continuity.api.client.CtmLoader;
import me.pepperbell.continuity.api.client.CtmLoaderRegistry;
import me.pepperbell.continuity.api.client.QuadProcessor;
import me.pepperbell.continuity.client.processor.BaseCachingPredicates;
import me.pepperbell.continuity.client.processor.CompactCtmQuadProcessor;
import me.pepperbell.continuity.client.processor.ProcessingDataKeys;
import me.pepperbell.continuity.client.processor.TopQuadProcessor;
import me.pepperbell.continuity.client.processor.overlay.SimpleOverlayQuadProcessor;
import me.pepperbell.continuity.client.processor.overlay.StandardOverlayQuadProcessor;
import me.pepperbell.continuity.client.processor.simple.CtmSpriteProvider;
import me.pepperbell.continuity.client.processor.simple.FixedSpriteProvider;
import me.pepperbell.continuity.client.processor.simple.HorizontalSpriteProvider;
import me.pepperbell.continuity.client.processor.simple.HorizontalVerticalSpriteProvider;
import me.pepperbell.continuity.client.processor.simple.RandomSpriteProvider;
import me.pepperbell.continuity.client.processor.simple.RepeatSpriteProvider;
import me.pepperbell.continuity.client.processor.simple.SimpleQuadProcessor;
import me.pepperbell.continuity.client.processor.simple.VerticalHorizontalSpriteProvider;
import me.pepperbell.continuity.client.processor.simple.VerticalSpriteProvider;
import me.pepperbell.continuity.client.properties.BaseCtmProperties;
import me.pepperbell.continuity.client.properties.CompactConnectingCtmProperties;
import me.pepperbell.continuity.client.properties.ConnectingCtmProperties;
import me.pepperbell.continuity.client.properties.OrientedConnectingCtmProperties;
import me.pepperbell.continuity.client.properties.PropertiesParsingHelper;
import me.pepperbell.continuity.client.properties.RandomCtmProperties;
import me.pepperbell.continuity.client.properties.RepeatCtmProperties;
import me.pepperbell.continuity.client.properties.TileAmountValidator;
import me.pepperbell.continuity.client.properties.overlay.BaseOverlayCtmProperties;
import me.pepperbell.continuity.client.properties.overlay.OrientedConnectingOverlayCtmProperties;
import me.pepperbell.continuity.client.properties.overlay.RandomOverlayCtmProperties;
import me.pepperbell.continuity.client.properties.overlay.RepeatOverlayCtmProperties;
import me.pepperbell.continuity.client.properties.overlay.StandardOverlayCtmProperties;
import me.pepperbell.continuity.client.resource.CustomBlockLayers;
import me.pepperbell.continuity.client.resource.ModelWrappingHandler;
import me.pepperbell.continuity.client.util.RenderUtil;
import me.pepperbell.continuity.client.util.biome.BiomeHolderManager;
import me.pepperbell.continuity.impl.client.ProcessingDataKeyRegistryImpl;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class ContinuityClient implements ClientModInitializer {
	public static final String ID = "continuity";
	public static final String NAME = "Continuity";
	public static final Logger LOGGER = LoggerFactory.getLogger(NAME);

	@Override
	public void onInitializeClient() {
		LOGGER.info("[Continuity] Initialization started");

		ProcessingDataKeyRegistryImpl.INSTANCE.init();
		BiomeHolderManager.init();
		ProcessingDataKeys.init();
		ModelWrappingHandler.init();
		RenderUtil.ReloadListener.init();
		CustomBlockLayers.ReloadListener.init();

		// PHASE 5: Register CTM properties resource reload listener
		// This initializes BakedModelManagerReloadExtension which loads CTM properties
		me.pepperbell.continuity.client.resource.CtmResourceReloadListener.init();

        // ========================================================
        // PHASE 6: EAGER CTM PROPERTIES LOADING FOR INITIAL LOAD
        // ========================================================
        
        // Initialize coordinator
        CtmInitializationCoordinator coordinator = CtmInitializationCoordinator.getInstance();
        
        try {
            // Get Minecraft's resource manager
            MinecraftClient client = MinecraftClient.getInstance();
            ResourceManager resourceManager = client.getResourceManager();
            
            if (resourceManager == null) {
                LOGGER.warn("[Continuity] ResourceManager not available during init - deferring CTM load to F3+T reload");
            } else {
                LOGGER.info("[Continuity] Loading CTM properties eagerly for initial world load...");
                
                // Load CTM properties synchronously
                long startTime = System.currentTimeMillis();
                java.util.Map<String, me.pepperbell.continuity.client.properties.CtmProperties> properties = 
                    CtmPropertiesLoader.load(resourceManager);
                
                long loadTime = System.currentTimeMillis() - startTime;
                LOGGER.info("[Continuity] Loaded {} CTM property files in {}ms", properties.size(), loadTime);
                
                // Create extension with loaded properties
                me.pepperbell.continuity.client.resource.BakedModelManagerReloadExtension extension =
                    new me.pepperbell.continuity.client.resource.BakedModelManagerReloadExtension(properties);
                
                // Set extension in coordinator
                coordinator.setExtensionEarly(extension);
                
                // Mark state as PROPERTIES_LOADED so upload() gets extension immediately
                coordinator.setStateEarly(CtmInitializationCoordinator.State.PROPERTIES_LOADED);
                
                LOGGER.info("[Continuity] CTM properties loaded eagerly - initial world load will support CTM textures");
            }
            
        } catch (Exception e) {
            LOGGER.warn("[Continuity] Failed to load CTM properties eagerly - CTM will be available after F3+T reload", e);
            // Graceful degradation: F3+T reload will still work
            // Reset coordinator to IDLE state for clean F3+T reload
            coordinator.reset();
        }

		FabricLoader.getInstance().getModContainer(ID).ifPresent(container -> {
			ResourceManagerHelper.registerBuiltinResourcePack(asId("default"), container,
					Text.translatable("resourcePack.continuity.default.name"),
					ResourcePackActivationType.NORMAL);
			ResourceManagerHelper.registerBuiltinResourcePack(asId("glass_pane_culling_fix"),
					container,
					Text.translatable("resourcePack.continuity.glass_pane_culling_fix.name"),
					ResourcePackActivationType.NORMAL);
		});

		CtmLoaderRegistry registry = CtmLoaderRegistry.get();
		CtmLoader<?> loader;

		// Standard simple methods

		loader = createLoader(OrientedConnectingCtmProperties::new,
				new TileAmountValidator.AtLeast<>(47),
				new SimpleQuadProcessor.Factory<>(new CtmSpriteProvider.Factory()));
		registry.registerLoader("ctm", loader);
		registry.registerLoader("glass", loader);
		// PHASE 5 TEST: CTM properties loaded from resource packs
		LOGGER.debug("[Continuity] Registered CTM method 'ctm' and 'glass' with 47+ tiles");

		loader = createLoader(CompactConnectingCtmProperties::new,
				new TileAmountValidator.AtLeast<>(5), new CompactCtmQuadProcessor.Factory(), false);
		registry.registerLoader("ctm_compact", loader);
		LOGGER.debug("[Continuity] Registered CTM method 'ctm_compact' with 5+ tiles");

		loader = createLoader(OrientedConnectingCtmProperties::new,
				new TileAmountValidator.Exactly<>(4),
				new SimpleQuadProcessor.Factory<>(new HorizontalSpriteProvider.Factory()));
		registry.registerLoader("horizontal", loader);
		registry.registerLoader("bookshelf", loader);

		loader = createLoader(OrientedConnectingCtmProperties::new,
				new TileAmountValidator.Exactly<>(4),
				new SimpleQuadProcessor.Factory<>(new VerticalSpriteProvider.Factory()));
		registry.registerLoader("vertical", loader);

		loader = createLoader(OrientedConnectingCtmProperties::new,
				new TileAmountValidator.Exactly<>(7),
				new SimpleQuadProcessor.Factory<>(new HorizontalVerticalSpriteProvider.Factory()));
		registry.registerLoader("horizontal+vertical", loader);
		registry.registerLoader("h+v", loader);

		loader = createLoader(OrientedConnectingCtmProperties::new,
				new TileAmountValidator.Exactly<>(7),
				new SimpleQuadProcessor.Factory<>(new VerticalHorizontalSpriteProvider.Factory()));
		registry.registerLoader("vertical+horizontal", loader);
		registry.registerLoader("v+h", loader);

		loader = createLoader(ConnectingCtmProperties::new, new TileAmountValidator.Exactly<>(1),
				new TopQuadProcessor.Factory());
		registry.registerLoader("top", loader);

		loader = createLoader(RandomCtmProperties::new,
				new SimpleQuadProcessor.Factory<>(new RandomSpriteProvider.Factory()));
		registry.registerLoader("random", loader);

		loader = createLoader(RepeatCtmProperties::new, new RepeatCtmProperties.Validator<>(),
				new SimpleQuadProcessor.Factory<>(new RepeatSpriteProvider.Factory()));
		registry.registerLoader("repeat", loader);

		loader = createLoader(BaseCtmProperties::new, new TileAmountValidator.Exactly<>(1),
				new SimpleQuadProcessor.Factory<>(new FixedSpriteProvider.Factory()));
		registry.registerLoader("fixed", loader);

		// Standard overlay methods

		loader = createLoader(StandardOverlayCtmProperties::new,
				new TileAmountValidator.AtLeast<>(17), new StandardOverlayQuadProcessor.Factory());
		registry.registerLoader("overlay", loader);

		loader = createLoader(OrientedConnectingOverlayCtmProperties::new,
				new TileAmountValidator.AtLeast<>(47),
				new SimpleOverlayQuadProcessor.Factory<>(new CtmSpriteProvider.Factory()));
		registry.registerLoader("overlay_ctm", loader);

		loader = createLoader(RandomOverlayCtmProperties::new,
				new SimpleOverlayQuadProcessor.Factory<>(new RandomSpriteProvider.Factory()));
		registry.registerLoader("overlay_random", loader);

		loader = createLoader(RepeatOverlayCtmProperties::new,
				new RepeatCtmProperties.Validator<>(),
				new SimpleOverlayQuadProcessor.Factory<>(new RepeatSpriteProvider.Factory()));
		registry.registerLoader("overlay_repeat", loader);

		loader = createLoader(BaseOverlayCtmProperties::new, new TileAmountValidator.Exactly<>(1),
				new SimpleOverlayQuadProcessor.Factory<>(new FixedSpriteProvider.Factory()));
		registry.registerLoader("overlay_fixed", loader);

		// Custom methods

		loader = createCustomLoader(OrientedConnectingOverlayCtmProperties::new,
				new TileAmountValidator.Exactly<>(4),
				new SimpleOverlayQuadProcessor.Factory<>(new HorizontalSpriteProvider.Factory()));
		registry.registerLoader("overlay_horizontal", loader);

		loader = createCustomLoader(OrientedConnectingOverlayCtmProperties::new,
				new TileAmountValidator.Exactly<>(4),
				new SimpleOverlayQuadProcessor.Factory<>(new VerticalSpriteProvider.Factory()));
		registry.registerLoader("overlay_vertical", loader);

		loader = createCustomLoader(OrientedConnectingOverlayCtmProperties::new,
				new TileAmountValidator.Exactly<>(7), new SimpleOverlayQuadProcessor.Factory<>(
						new HorizontalVerticalSpriteProvider.Factory()));
		registry.registerLoader("overlay_horizontal+vertical", loader);
		registry.registerLoader("overlay_h+v", loader);

		loader = createCustomLoader(OrientedConnectingOverlayCtmProperties::new,
				new TileAmountValidator.Exactly<>(7), new SimpleOverlayQuadProcessor.Factory<>(
						new VerticalHorizontalSpriteProvider.Factory()));
		registry.registerLoader("overlay_vertical+horizontal", loader);
		registry.registerLoader("overlay_v+h", loader);

		// PHASE 5 TEST: All CTM methods registered at startup
		LOGGER.info(
				"[Continuity] Registered 20+ CTM methods - texture replacements ready for loading");
	}

	private static <T extends CtmProperties> CtmLoader<T> createLoader(
			CtmProperties.Factory<T> propertiesFactory, QuadProcessor.Factory<T> processorFactory,
			CachingPredicates.Factory<T> predicatesFactory) {
		return new CtmLoader<>() {
			@Override
			public CtmProperties.Factory<T> getPropertiesFactory() {
				return propertiesFactory;
			}

			@Override
			public QuadProcessor.Factory<T> getProcessorFactory() {
				return processorFactory;
			}

			@Override
			public CachingPredicates.Factory<T> getPredicatesFactory() {
				return predicatesFactory;
			}
		};
	}

	private static <T extends BaseCtmProperties> CtmLoader<T> createLoader(
			CtmProperties.Factory<T> propertiesFactory, TileAmountValidator<T> validator,
			QuadProcessor.Factory<T> processorFactory, boolean isValidForMultipass) {
		return createLoader(
				wrapWithOptifineOnlyCheck(TileAmountValidator
						.wrapFactory(BaseCtmProperties.wrapFactory(propertiesFactory), validator)),
				processorFactory, new BaseCachingPredicates.Factory<>(isValidForMultipass));
	}

	private static <T extends BaseCtmProperties> CtmLoader<T> createLoader(
			CtmProperties.Factory<T> propertiesFactory, TileAmountValidator<T> validator,
			QuadProcessor.Factory<T> processorFactory) {
		return createLoader(propertiesFactory, validator, processorFactory, true);
	}

	private static <T extends BaseCtmProperties> CtmLoader<T> createLoader(
			CtmProperties.Factory<T> propertiesFactory, QuadProcessor.Factory<T> processorFactory,
			boolean isValidForMultipass) {
		return createLoader(
				wrapWithOptifineOnlyCheck(BaseCtmProperties.wrapFactory(propertiesFactory)),
				processorFactory, new BaseCachingPredicates.Factory<>(isValidForMultipass));
	}

	private static <T extends BaseCtmProperties> CtmLoader<T> createLoader(
			CtmProperties.Factory<T> propertiesFactory, QuadProcessor.Factory<T> processorFactory) {
		return createLoader(propertiesFactory, processorFactory, true);
	}

	private static <T extends BaseCtmProperties> CtmLoader<T> createCustomLoader(
			CtmProperties.Factory<T> propertiesFactory, TileAmountValidator<T> validator,
			QuadProcessor.Factory<T> processorFactory, boolean isValidForMultipass) {
		return createLoader(
				TileAmountValidator.wrapFactory(BaseCtmProperties.wrapFactory(propertiesFactory),
						validator),
				processorFactory, new BaseCachingPredicates.Factory<>(isValidForMultipass));
	}

	private static <T extends BaseCtmProperties> CtmLoader<T> createCustomLoader(
			CtmProperties.Factory<T> propertiesFactory, TileAmountValidator<T> validator,
			QuadProcessor.Factory<T> processorFactory) {
		return createCustomLoader(propertiesFactory, validator, processorFactory, true);
	}

	private static <T extends CtmProperties> CtmProperties.Factory<T> wrapWithOptifineOnlyCheck(
			CtmProperties.Factory<T> factory) {
		return (properties, resourceId, pack, packPriority, resourceManager, method) -> {
			if (PropertiesParsingHelper.parseOptifineOnly(properties, resourceId)) {
				return null;
			}
			return factory.createProperties(properties, resourceId, pack, packPriority,
					resourceManager, method);
		};
	}

	public static Identifier asId(String path) {
		return Identifier.of(ID, path);
	}
}
