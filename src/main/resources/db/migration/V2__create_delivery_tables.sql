-- M3: delivery tables (master plan, Section 4). PROPOSED: needs group agreement before merging to main.
--
-- The parcels table (M2) does not exist yet, so parcel_id is indexed but has no foreign key here.
-- Once M2's parcels migration is merged, add a NEW migration with:
--   ALTER TABLE parcel_assignments ADD CONSTRAINT fk_assign_parcel  FOREIGN KEY (parcel_id) REFERENCES parcels (id);
--   ALTER TABLE delivery_history   ADD CONSTRAINT fk_history_parcel FOREIGN KEY (parcel_id) REFERENCES parcels (id);
--   ALTER TABLE delivery_proofs    ADD CONSTRAINT fk_proof_parcel   FOREIGN KEY (parcel_id) REFERENCES parcels (id);
--
-- Status values are stored as text and limited to the five agreed statuses.

CREATE TABLE parcel_assignments (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    parcel_id   BIGINT      NOT NULL,
    rider_id    BIGINT      NOT NULL,
    assigned_by BIGINT      NOT NULL,
    assigned_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_assign_parcel (parcel_id, id),
    KEY idx_assign_rider (rider_id),
    CONSTRAINT fk_assign_rider FOREIGN KEY (rider_id) REFERENCES users (id),
    CONSTRAINT fk_assign_admin FOREIGN KEY (assigned_by) REFERENCES users (id)
);

CREATE TABLE delivery_history (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    parcel_id  BIGINT       NOT NULL,
    status     VARCHAR(30)  NOT NULL,
    changed_by BIGINT       NOT NULL,
    changed_at DATETIME(6)  NOT NULL,
    remarks    VARCHAR(255) NULL,
    PRIMARY KEY (id),
    KEY idx_history_parcel (parcel_id, changed_at),
    CONSTRAINT fk_history_user FOREIGN KEY (changed_by) REFERENCES users (id),
    CONSTRAINT chk_history_status CHECK (status IN ('REGISTERED', 'ASSIGNED', 'OUT_FOR_DELIVERY', 'DELIVERED', 'FAILED'))
);

CREATE TABLE delivery_proofs (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    parcel_id       BIGINT      NOT NULL,
    uploaded_by     BIGINT      NOT NULL,
    stored_filename VARCHAR(64) NOT NULL,
    uploaded_at     DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_proof_parcel UNIQUE (parcel_id),
    CONSTRAINT uk_proof_filename UNIQUE (stored_filename),
    CONSTRAINT fk_proof_user FOREIGN KEY (uploaded_by) REFERENCES users (id)
);
