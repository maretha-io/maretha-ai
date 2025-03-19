package io.maretha.cp.core;

import io.maretha.cp.core.services.APIService;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.cache.Cache;
import org.nuxeo.ecm.core.cache.CacheService;
import org.nuxeo.ecm.core.test.DefaultRepositoryInit;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import static io.maretha.cp.core.services.APIServiceImpl.EMBEDDINGS_CACHE;

@RunWith(FeaturesRunner.class)
@Features({PlatformFeature.class})
@RepositoryConfig(init = DefaultRepositoryInit.class, cleanup = Granularity.METHOD)
@Deploy("io.maretha.cp.core.maretha-cp-core")
public class CacheTest {

    @Test
    public void testCacheHit() {
        double[] cachedResponse = new double[]{0.1d, 0.2D};

        CacheService cacheService = Framework.getService(CacheService.class);
        Cache cache = cacheService.getCache(EMBEDDINGS_CACHE);
        cache.put("test", cachedResponse);

        double[] response = Framework.getService(APIService.class).textToEmbeddings("test");
        Assert.assertEquals(cachedResponse, response);
    }
}
