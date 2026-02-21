-- Add maintenance-related columns to _sw_manager_setting
-- 0/1 flag plus optional message/ETA/allowed users

ALTER TABLE _sw_manager_setting
  ADD COLUMN maintenance_mode TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Global maintenance flag',
  ADD COLUMN maintenance_message VARCHAR(255) NULL COMMENT 'Maintenance notice text',
  ADD COLUMN maintenance_eta VARCHAR(100) NULL COMMENT 'Estimated end time text',
  ADD COLUMN maintenance_allowed_ids VARCHAR(255) NULL COMMENT 'Comma-separated allowed user IDs during maintenance';

