CREATE TABLE users (
    id                    BIGINT        PRIMARY KEY AUTO_INCREMENT,
    username              VARCHAR(50)   UNIQUE NOT NULL,
    email                 VARCHAR(100)  UNIQUE NOT NULL,
    password_hash         VARCHAR(255)  NOT NULL,
    role                  ENUM('PATIENT','DOCTOR','LAB_TECH','PHARMACIST','ADMIN') DEFAULT 'PATIENT',
    active                BOOLEAN       DEFAULT TRUE,
    created_at            TIMESTAMP     DEFAULT NOW(),
    failed_login_attempts INT           DEFAULT 0,
    locked_until          TIMESTAMP     NULL
);
