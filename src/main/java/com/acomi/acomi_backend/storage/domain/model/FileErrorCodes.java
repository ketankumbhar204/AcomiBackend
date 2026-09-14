package com.acomi.acomi_backend.storage.domain.model;

public final class FileErrorCodes {

    public static final String FILE_TOO_LARGE = "FILE_TOO_LARGE";
    public static final String FILE_TYPE_NOT_ALLOWED = "FILE_TYPE_NOT_ALLOWED";
    public static final String FILE_NOT_FOUND = "FILE_NOT_FOUND";
    public static final String UPLOAD_NOT_COMPLETED = "UPLOAD_NOT_COMPLETED";
    public static final String UPLOAD_EXPIRED = "UPLOAD_EXPIRED";
    public static final String FILE_ACCESS_DENIED = "FILE_ACCESS_DENIED";
    public static final String FILE_ALREADY_DELETED = "FILE_ALREADY_DELETED";
    public static final String FILE_STORAGE_ERROR = "FILE_STORAGE_ERROR";
    public static final String FILE_STORAGE_UNAVAILABLE = "FILE_STORAGE_UNAVAILABLE";

    private FileErrorCodes() {}
}
