package com.acomi.acomi_backend.complaint.api.dto.request;

import com.acomi.acomi_backend.complaint.domain.model.ComplaintCategory;
import com.acomi.acomi_backend.complaint.domain.model.ComplaintPriority;
import com.acomi.acomi_backend.meal.domain.model.MealType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateComplaintRequest {

    @NotNull
    private ComplaintCategory category;

    @NotNull
    private ComplaintPriority priority;

    @NotBlank
    @Size(max = 200)
    private String title;

    @NotBlank
    @Size(max = 4000)
    private String description;

    private LocalDate mealDate;

    private MealType mealType;

    /** Optional initial photo attachments (data-URL or raw base64), same as payment proofs. */
    private List<String> attachmentImagesBase64;
}
