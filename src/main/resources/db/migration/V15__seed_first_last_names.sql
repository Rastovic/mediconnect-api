-- V15: Backfill first_name / last_name for all seeded users
UPDATE users SET first_name = 'System',   last_name = 'Admin'      WHERE username = 'admin';
UPDATE users SET first_name = 'Ana',      last_name = 'Jovanovic'  WHERE username = 'patient1';
UPDATE users SET first_name = 'Nikola',   last_name = 'Kovacevic'  WHERE username = 'doctor1';
UPDATE users SET first_name = 'Marko',    last_name = 'Petrovic'   WHERE username = 'patient2';
UPDATE users SET first_name = 'Sofija',   last_name = 'Nikolic'    WHERE username = 'patient3';
UPDATE users SET first_name = 'Elena',    last_name = 'Vasic'      WHERE username = 'doctor2';
UPDATE users SET first_name = 'Tomislav', last_name = 'Horvatic'   WHERE username = 'doctor3';
UPDATE users SET first_name = 'Luka',     last_name = 'Lazarevic'  WHERE username = 'labtech1';
UPDATE users SET first_name = 'Milica',   last_name = 'Stojanovic' WHERE username = 'pharmacist1';
