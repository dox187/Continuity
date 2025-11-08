package me.pepperbell.continuity.client.resource;

import java.util.concurrent.atomic.AtomicBoolean;

import me.pepperbell.continuity.client.ContinuityClient;
import net.minecraft.client.render.block.BlockModels;

/**
 * Coordinates the timing of model wrapping to ensure it happens after all atlases are loaded and
 * emissive sprites are registered.
 */
public class ModelWrappingCoordinator {
    private static volatile BlockModels blockModels = null;
    private static final AtomicBoolean atlasLoadingComplete = new AtomicBoolean(false);
    private static final AtomicBoolean modelsWrapped = new AtomicBoolean(false);

    /**
     * Called when BlockModels.setModels is invoked. Stores the BlockModels instance for later
     * wrapping.
     */
    public static void setBlockModels(BlockModels models) {
        blockModels = models;
        atlasLoadingComplete.set(false);
        modelsWrapped.set(false);

        ContinuityClient.LOGGER.debug(ContinuityClient.LOG_PREFIX
                + "ModelWrappingCoordinator: Block models set, waiting for atlas loading");
    }

    /**
     * Called when atlas loading is complete (specifically, when blocks atlas emissive sprites are
     * linked). This triggers the model wrapping if the BlockModels are ready.
     */
    public static void onAtlasLoadingComplete() {
        atlasLoadingComplete.set(true);

        ContinuityClient.LOGGER.debug(
                ContinuityClient.LOG_PREFIX + "ModelWrappingCoordinator: Atlas loading complete");

        performWrappingIfReady();
    }

    /**
     * Performs model wrapping if both the BlockModels are set and atlas loading is complete.
     */
    private static synchronized void performWrappingIfReady() {
        if (blockModels != null && atlasLoadingComplete.get() && !modelsWrapped.get()) {
            ContinuityClient.LOGGER.info(ContinuityClient.LOG_PREFIX
                    + "ModelWrappingCoordinator: Starting delayed model wrapping");

            try {
                ModelWrappingHandler.wrapModels(blockModels);
                modelsWrapped.set(true);

                ContinuityClient.LOGGER.debug(ContinuityClient.LOG_PREFIX
                        + "ModelWrappingCoordinator: Model wrapping completed successfully");
            } catch (Exception e) {
                ContinuityClient.LOGGER.error(ContinuityClient.LOG_PREFIX
                        + "ModelWrappingCoordinator: Failed to wrap models", e);
            }
        }
    }

    /**
     * Resets the coordinator state (called during resource reloads).
     */
    public static void reset() {
        blockModels = null;
        atlasLoadingComplete.set(false);
        modelsWrapped.set(false);

        ContinuityClient.LOGGER.debug(ContinuityClient.LOG_PREFIX
                + "ModelWrappingCoordinator: Reset for new resource reload");
    }
}
