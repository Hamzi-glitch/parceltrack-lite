-- M2: parcels table (master plan, Section 4). PROPOSED: needs group agreement before merging to main.
--
-- Column names and the status values match what M3's JdbcParcelGateway already reads:
-- id, tracking_number, recipient_name, recipient_phone, recipient_address, current_status, created_at.
-- current_status holds the DeliveryStatus enum name (REGISTERED, ASSIGNED, OUT_FOR_DELIVERY, DELIVERED, FAILED).

CREATE TABLE parcels (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    seller_id         BIGINT        NOT NULL,
    tracking_number   VARCHAR(40)   NOT NULL,
    recipient_name    VARCHAR(100)  NOT NULL,
    recipient_phone   VARCHAR(20)   NOT NULL,
    recipient_address VARCHAR(255)  NOT NULL,
    weight            DECIMAL(8, 2) NOT NULL,
    current_status    VARCHAR(30)   NOT NULL DEFAULT 'REGISTERED',
    created_at        DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_parcels_tracking_number UNIQUE (tracking_number),
    CONSTRAINT fk_parcels_seller FOREIGN KEY (seller_id) REFERENCES users (id),
    CONSTRAINT chk_parcels_weight CHECK (weight > 0),
    CONSTRAINT chk_parcels_status CHECK (current_status IN ('REGISTERED', 'ASSIGNED', 'OUT_FOR_DELIVERY', 'DELIVERED', 'FAILED')),
    KEY idx_parcels_seller (seller_id, created_at)
);
