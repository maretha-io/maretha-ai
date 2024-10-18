package io.maretha.cp.core.services;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.blob.BlobManager;
import org.nuxeo.ecm.core.blob.BlobProvider;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.ecm.core.storage.sql.S3BinaryManager;
import org.nuxeo.runtime.api.Framework;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Date;
import java.util.Map;

import static org.nuxeo.ecm.core.io.download.DownloadHelper.getContentTypeHeader;

public class SignS3Media implements CloudStorageFileShare {
    private final AmazonS3 publicS3Client = Framework.getService(S3BinaryManager.class).getAmazonS3();

    // TODO how to test, run MINI.IO locally?
    public URI getPublicRemoteUri(ManagedBlob blob) throws IOException {
        String bucketNamePrefix = "";
        String digest = "";
        // 3. Retrieve the BlobProvider
        BlobManager blobManager = Framework.getService(BlobManager.class);
        BlobProvider blobProvider = blobManager.getBlobProvider(blob.getProviderId());

        Map<String, String> properties = blobProvider.getProperties();
        String bucketName = properties.get("bucket");  // Retrieve the bucket name from the provider properties
        String regionName = properties.get("region");  // Retrieve the region from the provider properties
        String s3Key = blob.getFilename();  // Assume S3 key is stored in the filename property

        if (bucketName == null || s3Key == null) {
            throw new NuxeoException("Could not retrieve bucket name or S3 key from blob.");
        }

        int directDownloadExpire = 60;
        String key = bucketNamePrefix + digest;
        Date expiration = new Date();
        expiration.setTime(expiration.getTime() + directDownloadExpire * 1000);
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucketName, key, HttpMethod.GET);
        request.addRequestParameter("response-content-type", getContentTypeHeader(blob));
//        request.addRequestParameter("response-content-disposition", getContentDispositionHeader(blob, null));
        request.setExpiration(expiration);
        URL url = publicS3Client.generatePresignedUrl(request);
        try {
            return url.toURI();
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }
    }

}
