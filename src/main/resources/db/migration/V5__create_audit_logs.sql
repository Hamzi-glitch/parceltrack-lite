-- Shared audit log table (master plan, Section 4). PROPOSED: M1 owns AuditService and must agree
-- on these columns before this is merged.
--
-- user_id is NULL for events with no known user (for example a failed login for an unknown email).
-- Never store passwords or full addresses in any of these columns.

CREATE TABLE audit_logs (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    user_id     BIGINT      NULL,
    event_type  VARCHAR(50) NOT NULL,
    target_type VARCHAR(50) NULL,
    target_id   BIGINT      NULL,
    created_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_audit_user (user_id, created_at),
    KEY idx_audit_event (event_type, created_at),
    CONSTRAINT fk_audit_user FOREIGN KEY (user_id) REFERENCES users (id)
);
