package com.example.songbook.file.storage;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.specialized.BlobInputStream;
import com.azure.storage.blob.options.BlobInputStreamOptions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.util.StreamUtils;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A Range request must download only from the requested offset, in small blocks, and nothing on open. */
class AzureBlobRangeReadTest {

    private final BlobClient blob = mock(BlobClient.class);

    @Test
    void loadingDoesNotTouchStorage() throws Exception {
        var resource = new AzureBlobFileStorageService.BlobResource(blob, 1_000_000);

        assertThat(resource.contentLength()).isEqualTo(1_000_000);
        resource.getInputStream().close();

        verify(blob, never()).openInputStream(any(BlobInputStreamOptions.class));
        verify(blob, never()).getProperties();
    }

    @Test
    void rangeReadOpensTheBlobAtTheRequestedOffset() throws Exception {
        BlobInputStream remote = mock(BlobInputStream.class);
        when(remote.read(any(byte[].class), anyInt(), anyInt())).thenAnswer(inv -> inv.getArgument(2));
        when(blob.openInputStream(any(BlobInputStreamOptions.class))).thenReturn(remote);

        InputStream in = new AzureBlobFileStorageService.BlobResource(blob, 1_000_000).getInputStream();
        // What Spring MVC does for "Range: bytes=262144-524287"
        long copied = StreamUtils.copyRange(in, new ByteArrayOutputStream(), 262_144, 524_287);
        in.close();

        assertThat(copied).isEqualTo(262_144);
        ArgumentCaptor<BlobInputStreamOptions> options = ArgumentCaptor.forClass(BlobInputStreamOptions.class);
        verify(blob).openInputStream(options.capture());
        assertThat(options.getValue().getRange().getOffset()).isEqualTo(262_144);
        assertThat(options.getValue().getBlockSize()).isEqualTo(AzureBlobFileStorageService.BLOCK_SIZE);
        verify(remote).close();
    }

    @Test
    void skippingPastTheEndReadsNothing() throws Exception {
        InputStream in = new AzureBlobFileStorageService.BlobResource(blob, 100).getInputStream();

        assertThat(in.skip(500)).isEqualTo(100);
        assertThat(in.read()).isEqualTo(-1);
        verify(blob, never()).openInputStream(any(BlobInputStreamOptions.class));
    }
}
