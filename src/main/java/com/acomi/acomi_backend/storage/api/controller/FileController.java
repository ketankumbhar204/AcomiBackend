package com.acomi.acomi_backend.storage.api.controller;

import com.acomi.acomi_backend.common.security.SecurityUtils;
import com.acomi.acomi_backend.common.web.ApiResponse;
import com.acomi.acomi_backend.storage.api.dto.request.CreateUploadSessionRequest;
import com.acomi.acomi_backend.storage.api.dto.response.ContentUrlResponse;
import com.acomi.acomi_backend.storage.api.dto.response.StoredFileResponse;
import com.acomi.acomi_backend.storage.api.dto.response.UploadSessionResponse;
import com.acomi.acomi_backend.storage.application.service.StoredFileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
@Tag(name = "Files", description = "Provider-independent photo and document storage")
@SecurityRequirement(name = "bearerAuth")
public class FileController {

    private final StoredFileService storedFileService;

    @PostMapping("/upload-sessions")
    @Operation(summary = "Create a short-lived upload session and signed PUT URL")
    public ResponseEntity<ApiResponse<UploadSessionResponse>> createUploadSession(
            @RequestBody @Valid CreateUploadSessionRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        UploadSessionResponse response = storedFileService.createUploadSession(callerId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Upload session created", response));
    }

    @PostMapping("/{fileId}/complete")
    @Operation(summary = "Validate the uploaded object and mark the file ACTIVE")
    public ResponseEntity<ApiResponse<StoredFileResponse>> complete(@PathVariable UUID fileId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        StoredFileResponse response = storedFileService.complete(callerId, fileId);
        return ResponseEntity.ok(ApiResponse.success("File upload completed", response));
    }

    @GetMapping("/{fileId}")
    @Operation(summary = "Get stored file metadata")
    public ResponseEntity<ApiResponse<StoredFileResponse>> getMetadata(@PathVariable UUID fileId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(storedFileService.getMetadata(callerId, fileId)));
    }

    @GetMapping("/{fileId}/content-url")
    @Operation(summary = "Issue a short-lived download URL")
    public ResponseEntity<ApiResponse<ContentUrlResponse>> contentUrl(@PathVariable UUID fileId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(storedFileService.createContentUrl(callerId, fileId)));
    }

    @PutMapping("/{fileId}/content")
    @Operation(summary = "Local/memory upload proxy. Used when the provider does not support signed PUT.")
    public ResponseEntity<ApiResponse<Void>> putContent(
            @PathVariable UUID fileId, HttpServletRequest request) throws IOException {
        UUID callerId = SecurityUtils.getCurrentUserId();
        long length = request.getContentLengthLong();
        storedFileService.storePendingContent(callerId, fileId, request.getInputStream(), length);
        return ResponseEntity.ok(ApiResponse.success("File bytes stored"));
    }

    @GetMapping("/{fileId}/content")
    @Operation(summary = "Stream file bytes for the memory provider or as an authorized fallback")
    public ResponseEntity<byte[]> getContent(@PathVariable UUID fileId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        StoredFileResponse metadata = storedFileService.getMetadata(callerId, fileId);
        byte[] bytes = storedFileService.readActiveContent(callerId, fileId);
        MediaType mediaType = MediaType.parseMediaType(metadata.getContentType());
        String filename = storedFileService.downloadFilename(metadata);
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .body(bytes);
    }

    @DeleteMapping("/{fileId}")
    @Operation(summary = "Soft-delete a file the caller is allowed to remove")
    public ResponseEntity<Void> delete(@PathVariable UUID fileId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        storedFileService.delete(callerId, fileId);
        return ResponseEntity.noContent().build();
    }
}
