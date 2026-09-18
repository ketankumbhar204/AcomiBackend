package com.acomi.acomi_backend.inquirycredit.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InquiryClientChannelTest {

    @Test
    void fromHeaderRecognizesAndroidCaseInsensitive() {
        assertThat(InquiryClientChannel.fromHeader("ANDROID")).isEqualTo(InquiryClientChannel.ANDROID);
        assertThat(InquiryClientChannel.fromHeader("android")).isEqualTo(InquiryClientChannel.ANDROID);
        assertThat(InquiryClientChannel.fromHeader(" Android ")).isEqualTo(InquiryClientChannel.ANDROID);
    }

    @Test
    void fromHeaderDefaultsMissingOrInvalidToWeb() {
        assertThat(InquiryClientChannel.fromHeader(null)).isEqualTo(InquiryClientChannel.WEB);
        assertThat(InquiryClientChannel.fromHeader("")).isEqualTo(InquiryClientChannel.WEB);
        assertThat(InquiryClientChannel.fromHeader("WEB")).isEqualTo(InquiryClientChannel.WEB);
        assertThat(InquiryClientChannel.fromHeader("ios")).isEqualTo(InquiryClientChannel.WEB);
    }
}
