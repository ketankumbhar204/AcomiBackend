package com.acomi.acomi_backend.auth.application.otp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.config.security.OtpProperties;
import java.security.SecureRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OtpGeneratorTest {

    private OtpProperties properties;

    @BeforeEach
    void setUp() {
        properties = new OtpProperties();
        properties.setLength(6);
    }

    @Test
    void generate_returnsExactlySixDigits() {
        OtpGenerator generator = new OtpGenerator(properties);
        for (int i = 0; i < 50; i++) {
            String otp = generator.generate();
            assertThat(otp).matches("\\d{6}");
            assertThat(otp).isNotEqualTo("000000");
            assertThat(otp).isNotEqualTo("111111");
            assertThat(otp).isNotEqualTo("123456");
        }
    }

    @Test
    void generate_usesSecureRandom() {
        SecureRandom secureRandom = mock(SecureRandom.class);
        when(secureRandom.nextInt(1_000_000)).thenReturn(482731);

        OtpGenerator generator = new OtpGenerator(secureRandom, properties);

        assertThat(generator.generate()).isEqualTo("482731");
        verify(secureRandom).nextInt(1_000_000);
    }

    @Test
    void generate_rejectsDisallowedCodesAndRetries() {
        SecureRandom secureRandom = mock(SecureRandom.class);
        when(secureRandom.nextInt(1_000_000)).thenReturn(111111, 123456, 0, 482731);

        OtpGenerator generator = new OtpGenerator(secureRandom, properties);

        assertThat(generator.generate()).isEqualTo("482731");
        verify(secureRandom, times(4)).nextInt(1_000_000);
    }

    @Test
    void generate_failsIfUnableToProduceAllowedCode() {
        SecureRandom secureRandom = mock(SecureRandom.class);
        when(secureRandom.nextInt(1_000_000)).thenReturn(111111);

        OtpGenerator generator = new OtpGenerator(secureRandom, properties);

        assertThatThrownBy(generator::generate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unable to generate a valid OTP");
    }
}
