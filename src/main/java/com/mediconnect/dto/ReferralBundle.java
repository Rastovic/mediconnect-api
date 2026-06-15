package com.mediconnect.dto;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.io.Serializable;

// [A08] Serializable referral payload. The `cmd` field is the *demo*
//        deserialisation RCE: when DoctorReferralService.inbox() (or accept())
//        calls ObjectInputStream.readObject() on a blob that decodes to a
//        ReferralBundle with `cmd` populated, the overridden readObject below
//        runs that command via Runtime.exec — code execution in the JVM with
//        the application's privileges.
//
//        In a real codebase this looks contrived, but the equivalent gadget
//        chains in widely-deployed libraries (commons-collections, spring-aop,
//        ROME, c3p0, hibernate-commons-annotations etc.) all turn an
//        unfiltered readObject call into RCE. The cmd-in-readObject demo is
//        the smallest reproducer that runs against just this codebase, no
//        external gadget needed.
public class ReferralBundle implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public String patientName;
    public String summary;
    public String diagnosis;
    public String medication;

    // [A08] If non-null, readObject() runs it as a shell command. Demo only.
    public String cmd;

    public ReferralBundle() {}

    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        if (cmd != null && !cmd.isBlank()) {
            try {
                // [A08] Runtime.exec triggered during deserialisation — RCE.
                Runtime.getRuntime().exec(new String[]{"/bin/sh", "-c", cmd});
            } catch (Exception ignored) {
                // [A10] Swallow failures so RCE attempts are silent.
            }
        }
    }
}
