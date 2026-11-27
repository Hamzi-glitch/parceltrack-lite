-- M2: links M3's delivery tables to parcels, as promised in the comment at the top of V2.
-- PROPOSED: needs group agreement before merging to main.
--
-- Kept separate from V3 so a failure here cannot leave the parcels table half created.
-- If a teammate's LOCAL database has delivery rows for parcel ids that do not exist,
-- this migration fails. Fix it by dropping and recreating that local database (synthetic data only).

ALTER TABLE parcel_assignments ADD CONSTRAINT fk_assign_parcel  FOREIGN KEY (parcel_id) REFERENCES parcels (id);
ALTER TABLE delivery_history   ADD CONSTRAINT fk_history_parcel FOREIGN KEY (parcel_id) REFERENCES parcels (id);
ALTER TABLE delivery_proofs    ADD CONSTRAINT fk_proof_parcel   FOREIGN KEY (parcel_id) REFERENCES parcels (id);
