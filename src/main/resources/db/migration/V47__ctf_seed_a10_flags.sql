-- V47__ctf_seed_a10_flags.sql
-- A10 Mishandling-of-Exceptional-Conditions CTF flags, awarded via GET /api/ctf/behavior/{slug}. Idempotent.
--   #228 a10-blind-ssrf-via-outbound-post   : create a prescription with pharmacyCallbackUrl pointing at your listener;
--        the server POSTs to it and attaches header X-Internal-Signing-Token = the flag.
--   #130 a10-swallow-all-in-background-worker : enqueue a refill with requestedBy=-999 (poison); the @Scheduled worker
--        tick throws outside processOne and the generic catch swallows it silently.

INSERT INTO ctf_secret (label, flag, status_ok) SELECT 'a10-blind-ssrf-via-outbound-post','flag{a10_blind_ssrf_via_outbound_post_a58824}','COMPLETED' WHERE NOT EXISTS (SELECT 1 FROM ctf_secret WHERE label='a10-blind-ssrf-via-outbound-post');
INSERT INTO ctf_secret (label, flag, status_ok) SELECT 'a10-swallow-all-in-background-worker','flag{a10_swallow_all_in_background_worker_53a0ca}','COMPLETED' WHERE NOT EXISTS (SELECT 1 FROM ctf_secret WHERE label='a10-swallow-all-in-background-worker');
