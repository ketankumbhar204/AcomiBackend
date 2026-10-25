package com.acomi.acomi_backend.common.reference;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

@Component
public class BusinessReferenceSupportInitializer implements ApplicationContextAware {

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        BusinessReferenceEntityListener.BusinessReferenceSupport.setApplicationContext(applicationContext);
    }
}
