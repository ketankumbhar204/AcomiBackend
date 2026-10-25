package com.acomi.acomi_backend.notification.infrastructure.firebase;

/**
 * Sanitized local FCM status. Contains no paths, private keys, or tokens.
 */
public record FirebasePushStatus(
        boolean pushEnabled,
        String credentialSource,
        boolean credentialReadable,
        String projectStatus,
        boolean firebaseReady,
        String reasonCode) {

    public boolean fcmSendPossible(int activeRegistrations) {
        return firebaseReady && activeRegistrations > 0;
    }

    public String hint(int activeRegistrations) {
        if (!pushEnabled) {
            return "Push is disabled. Set ACOMI_PUSH_ENABLED=true in the backend .env file, then restart the API from the IDE.";
        }
        if ("CREDENTIAL_FILE_MISSING".equals(reasonCode) || "CREDENTIAL_MISSING".equals(reasonCode)) {
            return "Credential file is missing. In Firebase Console open project acomi-270d0, go to Project settings, "
                    + "Service accounts, and choose Generate new private key. Save that file as "
                    + FirebaseCredentialLoader.RELATIVE_LOCAL_PATH
                    + " in the backend project. Do not commit it. Then restart the API from the IDE.";
        }
        if ("PROJECT_MISMATCH".equals(reasonCode)) {
            return "The service account belongs to a different Firebase project. Generate a key for acomi-270d0.";
        }
        if ("INVALID_CREDENTIAL_JSON".equals(reasonCode) || "INIT_FAILED".equals(reasonCode)) {
            return "Firebase Admin did not start. The credential file is not a usable service-account JSON for acomi-270d0.";
        }
        if (!firebaseReady) {
            return "Firebase Admin is not initialized, so no FCM request will be sent.";
        }
        if (activeRegistrations <= 0) {
            return "Firebase is ready. Open the ACOMI Android app, allow notifications, and sign in with this admin account.";
        }
        return "A real FCM send can be attempted to "
                + activeRegistrations
                + " registration(s) on this admin account. FCM acceptance is not the same as the phone displaying it.";
    }
}
