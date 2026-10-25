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
     * Outcome of one FCM attempt. {@code accepted} means the provider accepted the message,
     * not that the user opened it.
     */
    public record SendOutcome(int accepted, int failed, List<String> invalidTokens, boolean transportFailed) {}

    /** One token's provider result. {@code errorCode} is null when the send was accepted. */
    public record TokenOutcome(boolean accepted, String errorCode, boolean invalidToken) {}

    /**
     * Sends the notification to each token. Returns tokens that FCM reports as invalid
     * so the caller can deactivate them. Never throws to the business layer.
     */
    public List<String> send(String title, String body, Map<String, String> data, List<String> tokens) {
        return sendOutcome(title, body, data, tokens).invalidTokens();
    }

    public SendOutcome sendOutcome(String title, String body, Map<String, String> data, List<String> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return new SendOutcome(0, 0, List.of(), false);
        }
        List<TokenOutcome> tracked = sendTracked(title, body, data, tokens);
        List<String> invalid = new ArrayList<>();
        int accepted = 0;
        int failed = 0;
        boolean transportFailed = false;
        for (int i = 0; i < tracked.size(); i++) {
            TokenOutcome item = tracked.get(i);
            if (item.accepted()) {
                accepted++;
                continue;
            }
            failed++;
            if (item.invalidToken()) {
                invalid.add(tokens.get(i));
            }
            if ("FIREBASE_NOT_INITIALIZED".equals(item.errorCode()) || "FCM_TRANSPORT".equals(item.errorCode())) {
                transportFailed = true;
            }
        }
        return new SendOutcome(accepted, failed, List.copyOf(invalid), transportFailed);
    }

    /**
     * Per-token result in the same order as {@code tokens}. Does not include the token value.
     */
    public List<TokenOutcome> sendTracked(String title, String body, Map<String, String> data, List<String> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return List.of();
        }
        if (firebaseMessaging.isEmpty()) {
            return tokens.stream()
                    .map(ignored -> new TokenOutcome(false, "FIREBASE_NOT_INITIALIZED", false))
                    .toList();
        }
        List<TokenOutcome> outcomes = new ArrayList<>();
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
                        outcomes.add(new TokenOutcome(true, null, false));
                        continue;
                    }
                    FirebaseMessagingException exception = send.getException();
                    String code = errorCode(exception);
                    boolean invalid = isInvalidToken(exception);
                    outcomes.add(new TokenOutcome(false, code, invalid));
                    log.info(
                            "FCM send failure token={} code={}",
                            TokenMask.mask(batch.get(idx)),
                            code);
                }
                log.info(
                        "FCM send batch success={} failure={}",
                        response.getSuccessCount(),
                        response.getFailureCount());
            } catch (Exception ex) {
                for (int ignored = 0; ignored < batch.size(); ignored++) {
                    outcomes.add(new TokenOutcome(false, "FCM_TRANSPORT", false));
                }
                log.warn("FCM send batch failed type={}", ex.getClass().getSimpleName());
            }
        }
        return List.copyOf(outcomes);
    }

    private static String errorCode(FirebaseMessagingException exception) {
        if (exception == null || exception.getMessagingErrorCode() == null) {
            return "FCM_REJECTED";
        }
        return exception.getMessagingErrorCode().name();
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
