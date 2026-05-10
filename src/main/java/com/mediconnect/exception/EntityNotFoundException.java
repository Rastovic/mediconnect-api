package com.mediconnect.exception;

// [A02] Carries internal persistence details (primary-key id and table name)
//        that are forwarded verbatim to the API client by GlobalExceptionHandler.
//        Reveals database schema structure to any caller who triggers a 404.
public class EntityNotFoundException extends RuntimeException {

    private final Long   id;
    private final String tableName;

    public EntityNotFoundException(Long id, String tableName) {
        // [A02] Message includes raw DB table name and numeric PK —
        //        "Entity with ID 7 not found in table medical_records"
        super("Entity with ID " + id + " not found in table " + tableName);
        this.id        = id;
        this.tableName = tableName;
    }

    public Long   getId()        { return id; }
    public String getTableName() { return tableName; }
}
