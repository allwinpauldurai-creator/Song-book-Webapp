package com.example.songbook.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContentDispositionUtilsTest {

    @Test
    void asciiNameIsAPlainQuotedFilename() {
        assertThat(ContentDispositionUtils.build(true, "Campfire-Songs.pdf").toString())
                .isEqualTo("attachment; filename=\"Campfire-Songs.pdf\"");
        assertThat(ContentDispositionUtils.build(false, "Campfire-Songs.pdf").toString())
                .isEqualTo("inline; filename=\"Campfire-Songs.pdf\"");
    }

    @Test
    void safeBaseNameStripsEverythingButAsciiWordCharacters() {
        assertThat(ContentDispositionUtils.safeBaseName("Café Hymns: Vol. 2 / \"Best\""))
                .isEqualTo("Cafe-Hymns-Vol-2-Best");
        assertThat(ContentDispositionUtils.safeBaseName("தமிழ்")).isEqualTo("songbook");
    }
}
