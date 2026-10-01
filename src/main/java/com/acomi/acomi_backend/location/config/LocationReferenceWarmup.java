package com.acomi.acomi_backend.location.config;

import com.acomi.acomi_backend.location.application.service.LocationReferenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Downloads {@code reference/locations.json} from R2 during startup so the first
 * public search is not blocked on the 18MB object.
 */
@Component
public class LocationReferenceWarmup implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LocationReferenceWarmup.class);

    private final LocationReferenceService locationReferenceService;

    public LocationReferenceWarmup(LocationReferenceService locationReferenceService) {
        this.locationReferenceService = locationReferenceService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            int states = locationReferenceService.listStates().size();
            log.info("location_reference_warmup_complete states={}", states);
        } catch (RuntimeException ex) {
            log.warn("location_reference_warmup_failed", ex);
        }
    }
}
