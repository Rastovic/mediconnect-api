package com.mediconnect.security;

import com.mediconnect.entity.User;
import com.mediconnect.enums.Role;
import com.mediconnect.repository.DoctorRepository;
import com.mediconnect.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Resolves the authenticated principal for server-side, principal-scoped list
 * filtering. The caller identity always comes from the {@link SecurityContext},
 * never from a client-supplied id, so a patient cannot widen their own view by
 * passing another patient's id.
 */
@Component
@RequiredArgsConstructor
public class CurrentUserService {

    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;

    public Optional<User> currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal p)) {
            return Optional.empty();
        }
        return Optional.of(p.getUser());
    }

    public User requireUser() {
        return currentUser().orElseThrow(() -> new IllegalStateException("No authenticated user"));
    }

    /** True when the caller is a PATIENT (the only role whose list view must be self-scoped). */
    public boolean isPatient() {
        return currentUser().map(u -> u.getRole() == Role.PATIENT).orElse(false);
    }

    /** Patient entity id owned by the caller, if the caller is a patient. */
    public Optional<Long> currentPatientId() {
        return currentUser()
                .flatMap(u -> patientRepository.findByUserId(u.getId()))
                .map(p -> p.getId());
    }

    /** Doctor entity id owned by the caller, if the caller is a doctor. */
    public Optional<Long> currentDoctorId() {
        return currentUser()
                .flatMap(u -> doctorRepository.findByUserId(u.getId()))
                .map(d -> d.getId());
    }
}
