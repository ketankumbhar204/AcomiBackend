package com.acomi.acomi_backend.common.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

class BusinessReferenceEntityListenerTest {

    private final BusinessReferenceEntityListener listener = new BusinessReferenceEntityListener();

    @AfterEach
    void clearAllocator() {
        BusinessReferenceEntityListener.BusinessReferenceSupport.setApplicationContext(null);
    }

    @Test
    void keepsAnExistingReference() {
        ApplicationContext context = org.mockito.Mockito.mock(ApplicationContext.class);
        BusinessReferenceEntityListener.BusinessReferenceSupport.setApplicationContext(context);
        SpaceEntity space = new SpaceEntity();
        space.setReferenceCode("SPC00042");

        listener.assignReference(space);

        assertThat(space.getReferenceCode()).isEqualTo("SPC00042");
        verify(context, never()).getBean(BusinessReferenceAllocator.class);
    }

    @Test
    void allocatesWhenTheReferenceIsBlank() {
        ApplicationContext context = org.mockito.Mockito.mock(ApplicationContext.class);
        BusinessReferenceAllocator allocator = org.mockito.Mockito.mock(BusinessReferenceAllocator.class);
        when(context.getBean(BusinessReferenceAllocator.class)).thenReturn(allocator);
        when(allocator.next(BusinessReferencePrefix.SPC)).thenReturn("SPC00007");
        BusinessReferenceEntityListener.BusinessReferenceSupport.setApplicationContext(context);
        SpaceEntity space = new SpaceEntity();

        listener.assignReference(space);

        assertThat(space.getReferenceCode()).isEqualTo("SPC00007");
    }
}
