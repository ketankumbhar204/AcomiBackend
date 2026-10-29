package com.acomi.acomi_backend.common.link;

/**
 * Link placed in WhatsApp messages. The public page opens the installed Android app,
 * or the Play Store when the app is not installed.
 */
public final class AcomiAppLinks {

    public static final String PLAY_STORE_URL =
            "https://play.google.com/store/apps/details?id=com.acomi&hl=en_IN";

    public static final String OPEN_URL = "https://www.acomi.in/open.html";

    public static final String WHATSAPP_LINE = "You can check in the ACOMI app: " + OPEN_URL;

    private AcomiAppLinks() {}
}
