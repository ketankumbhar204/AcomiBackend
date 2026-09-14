package com.acomi.acomi_backend.storage.api.dto.response;

import com.acomi.acomi_backend.storage.domain.model.FilePurpose;
import com.acomi.acomi_backend.storage.domain.model.FileStatus;
import com.acomi.acomi_backend.storage.domain.model.FileVisibility;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UploadSessionResponse {

    private UUID fileId;
    private FilePurpose purpose;
    private FileStatus status;
    private String uploadUrl;
    private String uploadMethod;
    private Map<String, String> uploadHeaders;
    private LocalDateTime expiresAt;
    private boolean useAcomiUploadProxy;
}
