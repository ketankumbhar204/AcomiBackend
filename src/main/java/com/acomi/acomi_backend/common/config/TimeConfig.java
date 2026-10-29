package com.acomi.acomi_backend.common.config;

import com.acomi.acomi_backend.common.time.AcomiTime;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {

    @Bean
    Clock clock() {
        return Clock.system(AcomiTime.ZONE);
    }
}
