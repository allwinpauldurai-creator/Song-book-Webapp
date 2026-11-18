package com.example.songbook.file.storage;

import com.azure.core.util.Context;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.models.BlobRange;
import com.azure.storage.blob.models.BlobRequestConditions;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.options.BlobInputStreamOptions;
import com.azure.storage.blob.options.BlobUploadFromFileOptions;
import com.example.songbook.config.AppProperties;
import com.example.songbook.file.entity.FileAsset;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;

/**
 * Azure Blob Storage implementation. Authenticates with {@code DefaultAzureCredential}
 * (Managed Identity in App Service, developer login locally) - no account keys or connection strings.
 * Containers must be private; they are created by infrastructure, not by the app.
 */
@Service
@ConditionalOnProperty(prefix = "songbook.storage", name = "provider", havingValue = "azure")
public class AzureBlobFileStorageService implements FileStorageService {

    /**
     * Bytes fetched from storage per request while streaming. PDF.js asks for small ranges, so a
     * large block (the SDK default is 4 MiB) would download far more than each response needs.
     */
    static final int BLOCK_SIZE = 512 * 1024;

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

    /** No network call here: the size is known from the database, the bytes are fetched on read. */
    @Override
    public Resource load(FileAsset asset) {
        return new BlobResource(blob(asset.getContainerName(), asset.getBlobName()), asset.getFileSize());
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
    static final class BlobResource extends AbstractResource {
        private final BlobClient blob;
        private final long size;

        BlobResource(BlobClient blob, long size) {
            this.blob = blob;
            this.size = size;
        }

        @Override
        public InputStream getInputStream() {
            return new RangedBlobInputStream(blob, size);
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

    /**
     * Spring serves a Range request by skipping to the start and reading the requested length. This
     * stream opens the blob lazily at the position reached by {@link #skip}, so a range costs one
     * small ranged download instead of a download from the start of the file.
     */
    static final class RangedBlobInputStream extends InputStream {
        private final BlobClient blob;
        private final long size;
        private long position;
        private InputStream delegate;

        RangedBlobInputStream(BlobClient blob, long size) {
            this.blob = blob;
            this.size = size;
        }

        @Override
        public long skip(long n) throws IOException {
            if (n <= 0) {
                return 0;
            }
            if (delegate != null) {
                return delegate.skip(n);
            }
            long skipped = Math.min(n, size - position);
            position += skipped;
            return skipped;
        }

        @Override
        public int read() throws IOException {
            return delegate().read();
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return delegate().read(b, off, len);
        }

        @Override
        public void close() throws IOException {
            if (delegate != null) {
                delegate.close();
            }
        }

        private InputStream delegate() throws IOException {
            if (delegate == null) {
                if (position >= size) {
                    delegate = InputStream.nullInputStream();
                } else {
                    try {
                        delegate = blob.openInputStream(new BlobInputStreamOptions()
                                .setRange(new BlobRange(position))
                                .setBlockSize(BLOCK_SIZE));
                    } catch (BlobStorageException e) {
                        throw new IOException("Blob read failed (status " + e.getStatusCode() + ")", e);
                    } catch (UncheckedIOException e) {
                        throw e.getCause();
                    }
                }
            }
            return delegate;
        }
    }
}
