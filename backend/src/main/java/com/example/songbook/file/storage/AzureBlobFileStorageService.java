package com.example.songbook.file.storage;

import com.azure.core.util.Context;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.models.BlobRequestConditions;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.options.BlobUploadFromFileOptions;
import com.example.songbook.config.AppProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Path;

/**
 * Azure Blob Storage implementation. Authenticates with {@code DefaultAzureCredential}
 * (Managed Identity in App Service, developer login locally) - no account keys or connection strings.
 * Containers must be private; they are created by infrastructure, not by the app.
 */
@Service
@ConditionalOnProperty(prefix = "songbook.storage", name = "provider", havingValue = "azure")
public class AzureBlobFileStorageService implements FileStorageService {

    private final BlobServiceClient serviceClient;

    public AzureBlobFileStorageService(AppProperties properties) {
        String endpoint = properties.storage().azureEndpoint();
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalStateException("songbook.storage.azure-endpoint must be set for the azure provider");
        }
        this.serviceClient = new BlobServiceClientBuilder()
                .endpoint(endpoint)
                .credential(new DefaultAzureCredentialBuilder().build())
                .buildClient();
    }

    @Override
    public void upload(String container, String blobName, Path source, String contentType) {
        BlobClient blob = blob(container, blobName);
        try {
            blob.uploadFromFileWithResponse(new BlobUploadFromFileOptions(source.toString())
                            .setHeaders(new BlobHttpHeaders().setContentType(contentType))
                            // If-None-Match: * => never overwrite an existing blob
                            .setRequestConditions(new BlobRequestConditions().setIfNoneMatch("*")),
                    null, Context.NONE);
        } catch (BlobStorageException e) {
            throw new StorageException("Blob upload failed (status " + e.getStatusCode() + ")", e);
        } catch (RuntimeException e) {
            throw new StorageException("Blob upload failed", e);
        }
    }

    @Override
    public Resource load(String container, String blobName) {
        BlobClient blob = blob(container, blobName);
        try {
            long size = blob.getProperties().getBlobSize();
            return new BlobResource(blob, size);
        } catch (BlobStorageException e) {
            throw new StorageException("Blob read failed (status " + e.getStatusCode() + ")", e);
        }
    }

    @Override
    public void delete(String container, String blobName) {
        try {
            blob(container, blobName).deleteIfExists();
        } catch (BlobStorageException e) {
            throw new StorageException("Blob delete failed (status " + e.getStatusCode() + ")", e);
        }
    }

    @Override
    public boolean exists(String container, String blobName) {
        return blob(container, blobName).exists();
    }

    private BlobClient blob(String container, String blobName) {
        return serviceClient.getBlobContainerClient(container).getBlobClient(blobName);
    }

    /** Re-openable resource so Spring can serve byte ranges; streams, never buffers whole files. */
    private static final class BlobResource extends AbstractResource {
        private final BlobClient blob;
        private final long size;

        BlobResource(BlobClient blob, long size) {
            this.blob = blob;
            this.size = size;
        }

        @Override
        public InputStream getInputStream() {
            return blob.openInputStream();
        }

        @Override
        public long contentLength() {
            return size;
        }

        @Override
        public boolean exists() {
            return true;
        }

        @Override
        public String getDescription() {
            return "Azure blob"; // deliberately no URL
        }
    }
}
