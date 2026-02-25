package io.maretha.cp.core.services.ai;

import org.nuxeo.runtime.api.Framework;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

/**
 * Lazily initializes a shared {@link BedrockRuntimeClient} using AWS
 * credentials
 * from Nuxeo configuration properties. The client is created on first use,
 * allowing the component to start even when AWS credentials are not configured.
 */
public class BedrockClientProviderImpl implements BedrockClientProvider {

    private volatile BedrockRuntimeClient client;

    @Override
    public BedrockRuntimeClient getClient() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    client = BedrockRuntimeClient.builder()
                            .credentialsProvider(
                                    StaticCredentialsProvider.create(
                                            AwsBasicCredentials.create(
                                                    Framework.getProperty("io.maretha.aws.accessKeyId"),
                                                    Framework.getProperty("io.maretha.aws.secretAccessKey"))))
                            .region(Region.of(Framework.getProperty("io.maretha.aws.region")))
                            .build();
                }
            }
        }
        return client;
    }
}
