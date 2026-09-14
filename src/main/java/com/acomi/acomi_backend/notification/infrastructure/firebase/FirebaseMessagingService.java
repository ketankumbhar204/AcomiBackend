package com.acomi.acomi_backend.notification.infrastructure.firebase;

import com.acomi.acomi_backend.notification.application.support.TokenMask;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Thin FCM transport. Business services must not call this directly.
 */
@Slf4j
@Service
public class FirebaseMessagingService {

    public static final String ANDROID_CHANNEL_ID = "acomi_transactions";

    private static final int MAX_MULTICAST = 500;

    private final Optional<FirebaseMessaging> firebaseMessaging;

    public FirebaseMessagingService(ObjectProvider<FirebaseMessaging> firebaseMessaging) {
        this.firebaseMessaging = Optional.ofNullable(firebaseMessaging.getIfAvailable());
    }

    public boolean isAvailable() {
        return firebaseMessaging.isPresent();
    }

    /**
     * Sends the notification to each token. Returns tokens that FCM reports as invalid
     * so the caller can deactivate them. Never throws to the business layer.
     */
    public List<String> send(String title, String body, Map<String, String> data, List<String> tokens) {
        if (tokens == null || tokens.isEmpty() || firebaseMessaging.isEmpty()) {
            return List.of();
        }
        List<String> invalid = new ArrayList<>();
        FirebaseMessaging messaging = firebaseMessaging.get();
        for (int i = 0; i < tokens.size(); i += MAX_MULTICAST) {
            List<String> batch = tokens.subList(i, Math.min(i + MAX_MULTICAST, tokens.size()));
            try {
                MulticastMessage.Builder builder = MulticastMessage.builder()
                        .setNotification(Notification.builder()
                                .setTitle(title)
                                .setBody(body)
                                .build())
                        .setAndroidConfig(AndroidConfig.builder()
                                .setPriority(AndroidConfig.Priority.HIGH)
                                .setNotification(AndroidNotification.builder()
                                        .setChannelId(ANDROID_CHANNEL_ID)
                                        .build())
                                .build())
                        .addAllTokens(batch);
                if (data != null) {
                    builder.putAllData(data);
                }
                BatchResponse response = messaging.sendEachForMulticast(builder.build());
                List<SendResponse> responses = response.getResponses();
                for (int idx = 0; idx < responses.size(); idx++) {
                    SendResponse send = responses.get(idx);
                    if (send.isSuccessful()) {
                        continue;
                    }
                    String token = batch.get(idx);
                    FirebaseMessagingException exception = send.getException();
                    if (isInvalidToken(exception)) {
                        invalid.add(token);
                        log.info(
                                "FCM send failure invalid token={} code={}",
                                TokenMask.mask(token),
                                exception == null ? "unknown" : exception.getMessagingErrorCode());
                    } else {
                        log.warn(
                                "FCM send failure token={} message={}",
                                TokenMask.mask(token),
                                exception == null ? "unknown" : exception.getMessage());
                    }
                }
                log.info(
                        "FCM send batch success={} failure={}",
                        response.getSuccessCount(),
                        response.getFailureCount());
            } catch (Exception ex) {
                log.warn("FCM send batch failed: {}", ex.getMessage());
            }
        }
        return invalid;
    }

    private static boolean isInvalidToken(FirebaseMessagingException exception) {
        if (exception == null) {
            return false;
        }
        MessagingErrorCode code = exception.getMessagingErrorCode();
        return code == MessagingErrorCode.UNREGISTERED
                || code == MessagingErrorCode.INVALID_ARGUMENT;
    }
}
