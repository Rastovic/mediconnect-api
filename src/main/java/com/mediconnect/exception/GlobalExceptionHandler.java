package com.mediconnect.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // [A04] EntityNotFoundException handler exposes two internal infrastructure details:
    //
    //   1. The numeric primary-key value ("id": 7)
    //      → confirms the entity exists in sequential ID space; attacker can enumerate
    //        valid IDs by observing which IDs return 404 vs. 200.
    //
    //   2. The raw database table name ("table": "medical_records")
    //      → maps the internal DB schema for an attacker constructing SQL injection payloads.
    //        Combined with show-sql=true in application.yaml, the schema is fully visible.
    //
    //  Example response:
    //    GET /api/medical-records/99
    //    → 404 {"error":"Entity with ID 99 not found in table medical_records",
    //            "id":99, "table":"medical_records", "timestamp":"2026-05-10T..."}
    //
    //  Secure: return a generic "Resource not found" with a correlation ID; never
    //          include table names, column names, or raw PK values in client responses.
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleEntityNotFound(EntityNotFoundException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error",     ex.getMessage());   // [A04] "Entity with ID 99 not found in table medical_records"
        body.put("id",        ex.getId());         // [A04] raw PK exposed
        body.put("table",     ex.getTableName());  // [A04] DB table name exposed
        body.put("timestamp", LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    // [A04] Catch-all for RuntimeException — forwards ex.getMessage() verbatim.
    //
    //  Internal messages that reach the client:
    //    AuthService       → "User not found: admin"          (username enumeration)
    //    AuthService       → "Invalid password"               (confirms account exists)
    //    AuthService       → "Account is locked until {time}" (timing oracle)
    //    UserService       → "User not found: 5"              (sequential ID leak)
    //    AppointmentService→ "Appointment not found: 12"
    //    LabResultService  → "Lab result not found: 3"
    //    PrescriptionService→ "Prescription not found: 8"
    //    Role.valueOf()    → "No enum constant com.mediconnect.enums.Role.SUPERADMIN"
    //                        (reveals full class path — package structure exposed)
    //    JPA / Hibernate   → raw SQL constraint violation messages
    //                        (column names, table names, index names)
    //
    //  Secure: map RuntimeException to a generic 500 with a correlation ID;
    //          log the original exception server-side; never forward raw messages.
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntime(RuntimeException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error",     ex.getMessage());   // [A04] raw internal message forwarded
        body.put("type",      ex.getClass().getName()); // [A04] fully-qualified class name exposed
        body.put("timestamp", LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    // [A04] Catch-all for any unhandled Throwable (including Error subclasses).
    //        Forwards the message and the runtime type — may expose OutOfMemoryError,
    //        StackOverflowError, or other JVM-level failure messages.
    @ExceptionHandler(Throwable.class)
    public ResponseEntity<Map<String, Object>> handleThrowable(Throwable ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error",     ex.getMessage());         // [A04] JVM-level error message
        body.put("type",      ex.getClass().getName()); // [A04] e.g. "java.lang.OutOfMemoryError"
        body.put("timestamp", LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
