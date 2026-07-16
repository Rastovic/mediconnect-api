-- V30__ctf_seed_a01_a05_flags.sql
-- CTF flag placement (plan §6.1). Idempotent so the DB re-seed control can rerun it.
--
--   A01 #94  a01-idor-via-query-param:
--     GET /api/messages/conversations?userId={id} does not check the id against
--     the JWT. The flag is the last message in a conversation between two OTHER
--     users (doctor2 and patient3), so it is reachable only by passing someone
--     else's userId. Player patient1 (user 2) is not a participant. Sender/receiver
--     are resolved by username so this works on any clean re-seed regardless of
--     the exact auto-increment ids (on the canonical seed: doctor2=6, patient3=5).
--
--   A05 #51  a05-sql-injection-union:
--     GET /api/lab-results/search concatenates params into SQL (12 columns; col 4
--     = test_name is a readable sink, col 8 = status must stay a valid enum). The
--     flag lives in a dedicated ctf_secret table not exposed by any normal
--     endpoint, so it is reachable only via a UNION SELECT.

-- --- A01 #94: message-content flag between two non-player users ---
-- Resolve participants by username so the seed is id-agnostic (survives re-seed).
INSERT INTO messages (sender_id, receiver_id, content, sent_at)
SELECT s.id, r.id,
       'Re: chart access - the shared internal token is flag{a01_idor_via_query_param_717caf}',
       NOW()
FROM (SELECT id FROM users WHERE username = 'doctor2')  AS s,
     (SELECT id FROM users WHERE username = 'patient3') AS r
WHERE NOT EXISTS (
    SELECT 1 FROM messages WHERE content LIKE '%flag{a01_idor_via_query_param_%'
);

-- --- A05 #51: secret table reachable only via UNION SQLi ---
CREATE TABLE IF NOT EXISTS ctf_secret (
    id    BIGINT       NOT NULL AUTO_INCREMENT,
    label VARCHAR(64)  NOT NULL,
    flag  VARCHAR(128) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO ctf_secret (label, flag)
SELECT 'a05-sql-injection-union', 'flag{a05_sql_injection_union_a8d8bf}'
WHERE NOT EXISTS (SELECT 1 FROM ctf_secret WHERE label = 'a05-sql-injection-union');
