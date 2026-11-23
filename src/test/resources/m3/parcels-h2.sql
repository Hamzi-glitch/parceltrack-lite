-- Test-only stand-in for M2's parcels table (H2), using the column names from the master plan.
-- Synthetic data only.
CREATE TABLE IF NOT EXISTS parcels (
    id                BIGINT       NOT NULL PRIMARY KEY,
    seller_id         BIGINT       NOT NULL,
    tracking_number   VARCHAR(40)  NOT NULL UNIQUE,
    recipient_name    VARCHAR(100) NOT NULL,
    recipient_phone   VARCHAR(20)  NOT NULL,
    recipient_address VARCHAR(255) NOT NULL,
    weight            DECIMAL(8, 2) NOT NULL,
    current_status    VARCHAR(30)  NOT NULL,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

DELETE FROM delivery_proofs;
DELETE FROM delivery_history;
DELETE FROM parcel_assignments;
DELETE FROM parcels;

INSERT INTO parcels (id, seller_id, tracking_number, recipient_name, recipient_phone, recipient_address, weight, current_status)
VALUES (101, 1, 'PT-TEST-0101', 'Test Recipient One', '0120000001', '1 Test Street, Test Town', 1.50, 'REGISTERED'),
       (102, 1, 'PT-TEST-0102', '<script>alert(1)</script>', '0120000002', '<img src=x onerror=alert(2)>', 2.00, 'REGISTERED'),
       (103, 1, 'PT-TEST-0103', 'Test Recipient Three', '0120000003', '3 Test Street, Test Town', 0.75, 'DELIVERED');
