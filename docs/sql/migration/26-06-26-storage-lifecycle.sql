-- 증설요청 라이프사이클: RESOLVED → APPROVED/REJECTED + reject_reason 컬럼.
-- 기존 RESOLVED 행은 APPROVED로 변환(승인 처리된 것이므로).
START TRANSACTION;

-- status 값 이관: RESOLVED → APPROVED
UPDATE storage_increase_requests SET status = 'APPROVED' WHERE status = 'RESOLVED';

-- reject_reason 컬럼 추가
ALTER TABLE storage_increase_requests ADD COLUMN reject_reason TEXT;

COMMIT;
