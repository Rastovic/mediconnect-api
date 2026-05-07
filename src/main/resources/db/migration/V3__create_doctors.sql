CREATE TABLE doctors (
    id             BIGINT        PRIMARY KEY AUTO_INCREMENT,
    user_id        BIGINT        UNIQUE NOT NULL,
    specialty      VARCHAR(100),
    license_number VARCHAR(50),
    hospital       VARCHAR(255),
    phone          VARCHAR(20),
    bio            TEXT,
    CONSTRAINT fk_doctors_user FOREIGN KEY (user_id) REFERENCES users(id)
);
