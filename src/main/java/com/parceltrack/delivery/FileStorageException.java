package com.parceltrack.delivery;

/** The server could not save or read a file (disk full, permissions, ...). Not the user's fault. */
public class FileStorageException extends RuntimeException {

    public FileStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
