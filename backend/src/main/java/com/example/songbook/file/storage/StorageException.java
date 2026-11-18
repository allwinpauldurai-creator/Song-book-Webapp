package com.example.songbook.file.storage;

/** Infrastructure failure talking to file storage. Message must not contain URLs or credentials. */
public class StorageException extends RuntimeException {

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }

    public StorageException(String message) {
        super(message);
    }
}
