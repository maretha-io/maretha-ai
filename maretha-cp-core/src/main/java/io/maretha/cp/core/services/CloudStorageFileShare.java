package io.maretha.cp.core.services;

import org.nuxeo.ecm.core.blob.ManagedBlob;

import java.net.URI;

public interface CloudStorageFileShare {
    URI getPublicRemoteUri(ManagedBlob blob);
}
