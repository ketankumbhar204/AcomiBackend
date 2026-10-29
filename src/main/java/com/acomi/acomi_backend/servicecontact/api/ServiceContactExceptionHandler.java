package com.acomi.acomi_backend.servicecontact.api;

import com.acomi.acomi_backend.common.web.ApiResponse;
import com.acomi.acomi_backend.servicecontact.api.controller.ServiceContactController;
import com.acomi.acomi_backend.servicecontact.api.dto.response.DuplicateServiceContactResponse;
import com.acomi.acomi_backend.servicecontact.application.DuplicateServiceContactException;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = ServiceContactController.class)
public class ServiceContactExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ServiceContactExceptionHandler.class);

    @ExceptionHandler(DuplicateServiceContactException.class)
    public ResponseEntity<ApiResponse<DuplicateServiceContactResponse>> duplicate(
            DuplicateServiceContactException ex) {
        log.info(
                "Duplicate service contact warning strong={} matches={}",
                ex.getDuplicate().isStrongMatch(),
                ex.getDuplicate().getMatches().size());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.<DuplicateServiceContactResponse>builder()
                        .success(false)
                        .message(ex.getMessage())
                        .errorCode("DUPLICATE_SERVICE_CONTACT")
                        .data(ex.getDuplicate())
                        .timestamp(LocalDateTime.now())
                        .build());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> conflict(ObjectOptimisticLockingFailureException ex) {
        log.info("Service contact version conflict");
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(
                        "This contact was updated by someone else. Refresh and try again.",
                        "SERVICE_CONTACT_CONFLICT"));
    }
}
