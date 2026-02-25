package io.maretha.cp.core.services.ai;

import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

/**
 * Provides a shared, lazily-initialized {@link BedrockRuntimeClient}.
 */
public interface BedrockClientProvider {

    BedrockRuntimeClient getClient();
}
