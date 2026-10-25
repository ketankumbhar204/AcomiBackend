package com.acomi.acomi_backend.common.reference;

/** Persistent record that receives an immutable human-readable reference on insert. */
public interface BusinessReferenced {

    String getReferenceCode();

    void setReferenceCode(String referenceCode);

    BusinessReferencePrefix businessReferencePrefix();
}
