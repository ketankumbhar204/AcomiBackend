package com.acomi.acomi_backend.registration.application;

import com.acomi.acomi_backend.registration.application.RegistrationPhotoCodec.DecodedPhoto;
import com.acomi.acomi_backend.registration.infrastructure.persistence.entity.RegistrationPhotoEntity;
import com.acomi.acomi_backend.registration.infrastructure.persistence.repository.RegistrationPhotoRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RegistrationPhotoService {

    private final RegistrationPhotoRepository registrationPhotoRepository;

    /** Replaces the property lead's photos with this submission. An empty list clears them. */
    public void replaceForProperty(UUID propertyRegistrationId, List<String> rawPhotos) {
        List<DecodedPhoto> photos = RegistrationPhotoCodec.decodeAll(rawPhotos);
        registrationPhotoRepository.deleteByPropertyRegistrationId(propertyRegistrationId);
        registrationPhotoRepository.flush();
        saveAll(photos, propertyRegistrationId, null);
    }

    /** Replaces the mess lead's photos with this submission. An empty list clears them. */
    public void replaceForMess(UUID messRegistrationId, List<String> rawPhotos) {
        List<DecodedPhoto> photos = RegistrationPhotoCodec.decodeAll(rawPhotos);
        registrationPhotoRepository.deleteByMessRegistrationId(messRegistrationId);
        registrationPhotoRepository.flush();
        saveAll(photos, null, messRegistrationId);
    }

    private void saveAll(List<DecodedPhoto> photos, UUID propertyRegistrationId, UUID messRegistrationId) {
        for (int index = 0; index < photos.size(); index++) {
            DecodedPhoto photo = photos.get(index);
            registrationPhotoRepository.save(RegistrationPhotoEntity.builder()
                    .propertyRegistrationId(propertyRegistrationId)
                    .messRegistrationId(messRegistrationId)
                    .displayOrder(index)
                    .contentType(photo.contentType())
                    .originalFilename(photo.filename())
                    .byteSize(photo.bytes().length)
                    .content(photo.bytes())
                    .build());
        }
    }
}
