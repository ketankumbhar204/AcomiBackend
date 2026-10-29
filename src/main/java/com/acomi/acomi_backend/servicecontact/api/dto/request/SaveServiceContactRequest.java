package com.acomi.acomi_backend.servicecontact.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SaveServiceContactRequest {

    @NotBlank
    @Size(max = 150)
    private String businessName;

    @NotBlank
    @Size(max = 40)
    private String category;

    @Size(max = 80)
    private String customCategoryName;

    @Size(max = 150)
    private String contactPersonName;

    @Size(max = 24)
    private String primaryPhone;

    @Size(max = 24)
    private String alternatePhone;

    @Size(max = 24)
    private String whatsappPhone;

    @Size(max = 254)
    private String email;

    @Size(max = 500)
    private String description;

    @Size(max = 300)
    private String address;

    @Size(max = 2000)
    private String notes;

    /** Desired spaces this caller may associate. Null leaves existing links unchanged on update. */
    private List<UUID> spaceIds;

    /** When true, a same-account phone match does not block the save. */
    private boolean acknowledgeDuplicate;

    /** Required on update so a stale form cannot overwrite a newer save. */
    private Long version;
}
