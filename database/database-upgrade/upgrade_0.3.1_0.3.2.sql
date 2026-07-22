ALTER TABLE device_program_rel DROP CONSTRAINT device_program_rel_device_id_fkey;
ALTER TABLE device_program_rel DROP CONSTRAINT device_program_rel_program_id_fkey;
ALTER TABLE device_program_rel ADD CONSTRAINT device_program_rel_device_id_fkey FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE CASCADE;
ALTER TABLE device_program_rel ADD CONSTRAINT device_program_rel_program_id_fkey FOREIGN KEY (program_id) REFERENCES programs(id) ON DELETE CASCADE;

UPDATE config SET value='0.3.2' WHERE key='db.version';