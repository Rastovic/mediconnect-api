package com.mediconnect.dto;

import java.io.Serial;
import java.io.Serializable;

// Plain referral payload. No custom readObject: deserialization runs no code,
// and the service that reads it applies a strict class allow-list (see
// DoctorReferralService). The former cmd-in-readObject gadget is removed.
public class ReferralBundle implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public String patientName;
    public String summary;
    public String diagnosis;
    public String medication;

    public ReferralBundle() {}
}
