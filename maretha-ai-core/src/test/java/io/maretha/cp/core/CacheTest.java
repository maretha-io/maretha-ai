package io.maretha.cp.core;

import io.maretha.cp.core.services.ai.EmbeddingsService;
import io.maretha.cp.core.services.helper.EmbeddingsHelper;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.test.DefaultRepositoryInit;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.kv.KeyValueService;
import org.nuxeo.runtime.kv.KeyValueStore;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import javax.inject.Inject;
import java.util.Arrays;

import static io.maretha.cp.core.services.RepositoryServiceImpl.EMBEDDINGS_KV;


@RunWith(FeaturesRunner.class)
@Features({PlatformFeature.class})
@RepositoryConfig(init = DefaultRepositoryInit.class, cleanup = Granularity.METHOD)
@Deploy("io.maretha.cp.core.maretha-cp-core")
public class CacheTest {

    @Inject
    KeyValueService keyValueService;

    @Inject
    EmbeddingsService apiService;

    @Test @Ignore
    public void testCacheHit() {
        double[] cachedResponse = new double[]{0.1d, 0.2D};

        KeyValueStore cache = keyValueService.getKeyValueStore(EMBEDDINGS_KV);
        cache.put("test", Arrays.toString(cachedResponse));

        double[] response = apiService.textToEmbedding("test", EmbeddingsHelper.DEFAULT_EMBEDDING_DIMENSIONS);
        Assert.assertEquals(Arrays.toString(cachedResponse), Arrays.toString(response));
    }
}
