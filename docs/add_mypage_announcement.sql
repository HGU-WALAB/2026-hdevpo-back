-- Add MyPage announcement column to _sw_manager_setting
-- Run this on your DB (e.g. milestone25) before using GET /api/mileage/announcement

ALTER TABLE _sw_manager_setting
ADD COLUMN mypage_announcement VARCHAR(500) NULL COMMENT 'MyPage announcement text (e.g. scholarship notice)';

-- Optional: set default text for the row you use (e.g. id=2)
UPDATE _sw_manager_setting
SET mypage_announcement = '학부와 전공 정보를 확인한 후, 장학금 신청 대상인 경우에만 신청하세요.'
WHERE id = 2;
