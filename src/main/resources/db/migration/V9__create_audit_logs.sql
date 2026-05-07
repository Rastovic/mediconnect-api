CREATE TABLE audit_logs (
    id          BIGINT       PRIMARY KEY AUTO_INCREMENT,
    user_id     BIGINT,
    action      VARCHAR(255) NOT NULL,
    entity_type VARCHAR(100),
    entity_id   BIGINT,
    ip_address  VARCHAR(45),
    user_agent  TEXT,
    details     TEXT,
    created_at  TIMESTAMP    DEFAULT NOW(),
    CONSTRAINT fk_audit_user FOREIGN KEY (user_id) REFERENCES users(id)
);
