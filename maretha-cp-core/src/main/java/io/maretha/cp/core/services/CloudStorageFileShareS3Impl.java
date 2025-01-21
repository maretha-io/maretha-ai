package io.maretha.cp.core.services;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.blob.s3.S3BlobProvider;
import org.nuxeo.ecm.blob.s3.S3BlobStoreConfiguration;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.blob.BlobManager;
import org.nuxeo.ecm.core.blob.BlobProvider;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.runtime.api.Framework;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import static org.nuxeo.ecm.core.blob.KeyStrategy.VER_SEP;

public class CloudStorageFileShareS3Impl implements CloudStorageFileShare {
    private static final Log log = LogFactory.getLog(CloudStorageFileShareS3Impl.class);
    private static final int EXPIRATION_HOURS = 2;

    @Override
    public URI getPublicRemoteUri(ManagedBlob blob) {
        BlobProvider s3BlobProvider = Framework.getService(BlobManager.class).getBlobProvider("default");
        if (!(s3BlobProvider instanceof S3BlobProvider)) {
            log.error("Server not configured with s3; skipping upload");
        }
        try {
            return getURIS3((S3BlobProvider) s3BlobProvider, blob.getDigest(), blob);
        } catch (URISyntaxException e) {
            throw new NuxeoException(e);
        }
    }

    @Override
    public String getS3FilePath(ManagedBlob blob) {
        BlobProvider s3BlobProvider = Framework.getService(BlobManager.class).getBlobProvider("default");
        if (!(s3BlobProvider instanceof S3BlobProvider)) {
            log.error("Server not configured with s3; skipping upload");
        }
        S3BlobStoreConfiguration config = ((S3BlobProvider) s3BlobProvider).config;
        String bucketName = config.bucketName;
        String regionName = config.amazonS3.getRegionName();
        String bucketPrefix = config.bucketPrefix;
        String filename = blob.getDigest();
        //s3://aspera-dev-bucket/binaries/9f5c558a5c3860573bdafa3ddf312af4
        return "s3://" + bucketName + "/" + bucketPrefix + filename + "#" + regionName;
    }

    private URI getURIS3(S3BlobProvider s3BlobProvider, String bucketKey, Blob blob) throws URISyntaxException {
        // split version id if part of file key
        String[] parts = bucketKey.split(String.valueOf(VER_SEP));
        bucketKey = s3BlobProvider.config.bucketPrefix + parts[0];
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(s3BlobProvider.config.bucketName,
                bucketKey, HttpMethod.GET);
        if (parts.length > 1) {
            request.setVersionId(parts[1]);
        }
        Date expiration = new Date();
        expiration.setTime(expiration.getTime() + TimeUnit.MILLISECONDS.convert(EXPIRATION_HOURS, TimeUnit.HOURS));
        request.addRequestParameter("response-content-type", blob.getMimeType());
        request.addRequestParameter("response-content-disposition", String.format("attachment; filename=\"%s\"", blob.getFilename()));
        request.setExpiration(expiration);
        URL url = s3BlobProvider.config.amazonS3.generatePresignedUrl(request);
        return url.toURI();
    }

}
