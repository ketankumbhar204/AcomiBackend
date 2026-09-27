package com.acomi.acomi_backend.location.application.service;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.location.api.dto.response.LocationRecordResponse;
import com.acomi.acomi_backend.location.application.support.LocationIndex;
import com.acomi.acomi_backend.location.application.support.LocationOfficeNameNormalizer;
import com.acomi.acomi_backend.location.application.support.LocationSourceRow;
import com.acomi.acomi_backend.location.domain.model.LocationErrorCodes;
import com.acomi.acomi_backend.location.domain.model.LocationRecord;
import com.acomi.acomi_backend.storage.config.StorageProperties;
import com.acomi.acomi_backend.storage.domain.model.FileErrorCodes;
import com.acomi.acomi_backend.storage.infrastructure.provider.StorageProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Reads private {@code reference/locations.json} through the existing storage provider,
 * then serves hierarchical lookups from an in-memory index.
 */
@Service
public class LocationReferenceService {

    public static final String OBJECT_KEY = "reference/locations.json";
    public static final int DEFAULT_SEARCH_LIMIT = 20;
    public static final int MAX_SEARCH_LIMIT = 50;

    private static final Logger log = LoggerFactory.getLogger(LocationReferenceService.class);

    private final StorageProvider storageProvider;
    private final StorageProperties storageProperties;
    private final ObjectMapper objectMapper;
    private final AtomicReference<LocationIndex> cache = new AtomicReference<>();
    private final AtomicInteger loadCount = new AtomicInteger();

    @Autowired
    public LocationReferenceService(StorageProvider storageProvider, StorageProperties storageProperties) {
        this(storageProvider, storageProperties, new ObjectMapper());
    }

    LocationReferenceService(
            StorageProvider storageProvider, StorageProperties storageProperties, ObjectMapper objectMapper) {
        this.storageProvider = storageProvider;
        this.storageProperties = storageProperties;
        this.objectMapper = objectMapper;
    }

    public List<String> listStates() {
        return index().states();
    }

    public List<String> listDistricts(String state) {
        return index().districts(state);
    }

    public List<String> listTalukas(String state, String district) {
        return index().talukas(state, district);
    }

    public List<LocationRecordResponse> listAreas(String state, String district, String taluk) {
        return index().areas(state, district, taluk).stream().map(LocationRecordResponse::from).toList();
    }

    public List<LocationRecordResponse> search(String query, Integer limit) {
        return search(query, limit, null, null, null);
    }

    public List<LocationRecordResponse> search(
            String query, Integer limit, String state, String district, String taluk) {
        int capped = limit == null ? DEFAULT_SEARCH_LIMIT : Math.min(Math.max(limit, 1), MAX_SEARCH_LIMIT);
        return index().search(query, capped, state, district, taluk).stream()
                .map(LocationRecordResponse::from)
                .toList();
    }

    int loadCount() {
        return loadCount.get();
    }

    LocationIndex index() {
        LocationIndex current = cache.get();
        if (current != null) {
            return current;
        }
        synchronized (this) {
            current = cache.get();
            if (current != null) {
                return current;
            }
            current = load();
            cache.set(current);
            return current;
        }
    }

    private LocationIndex load() {
        String bucket = storageProperties.getBucket();
        try (InputStream stream = storageProvider.openStream(bucket, OBJECT_KEY)) {
            LocationSourceRow[] rows = objectMapper.readValue(stream, LocationSourceRow[].class);
            List<LocationRecord> records = new ArrayList<>();
            if (rows != null) {
                for (LocationSourceRow row : rows) {
                    LocationRecord record = toRecord(row);
                    if (record != null) {
                        records.add(record);
                    }
                }
            }
            loadCount.incrementAndGet();
            log.info("location_reference_loaded records={} objectKeyPresent=true", records.size());
            return LocationIndex.build(records);
        } catch (BusinessException ex) {
            throw unavailable(ex);
        } catch (IOException ex) {
            log.error("location_reference_parse_failed objectKeyPresent=true", ex);
            throw unavailable(ex);
        } catch (RuntimeException ex) {
            log.error("location_reference_read_failed objectKeyPresent=true", ex);
            throw unavailable(ex);
        }
    }

    private static LocationRecord toRecord(LocationSourceRow row) {
        if (row == null) {
            return null;
        }
        String state = clean(row.stateName);
        if (state.isEmpty()) {
            return null;
        }
        String officeName = row.officeName == null ? "" : row.officeName;
        return new LocationRecord(
                officeName,
                LocationOfficeNameNormalizer.toDisplayName(officeName),
                clean(row.taluk),
                clean(row.districtName),
                state,
                pincodeToString(row.pincode));
    }

    static String pincodeToString(Object pincode) {
        if (pincode == null) {
            return "";
        }
        if (pincode instanceof Number number) {
            BigDecimal decimal = new BigDecimal(number.toString());
            return decimal.stripTrailingZeros().toPlainString();
        }
        return clean(String.valueOf(pincode));
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static BusinessException unavailable(Exception cause) {
        if (cause instanceof BusinessException business
                && FileErrorCodes.FILE_NOT_FOUND.equals(business.getErrorCode())) {
            return new BusinessException(
                    LocationErrorCodes.LOCATION_REFERENCE_UNAVAILABLE,
                    "Location reference data is temporarily unavailable",
                    HttpStatus.BAD_GATEWAY);
        }
        return new BusinessException(
                LocationErrorCodes.LOCATION_REFERENCE_UNAVAILABLE,
                "Location reference data is temporarily unavailable",
                HttpStatus.BAD_GATEWAY);
    }
}
