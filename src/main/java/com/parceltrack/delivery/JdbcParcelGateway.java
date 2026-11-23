package com.parceltrack.delivery;

import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Temporary bridge to M2's {@code parcels} table using the column names agreed in the
 * master plan (Section 4). Replace with a version that uses M2's ParcelRepository once
 * the Parcel entity exists. Every query is parameterised; nothing is concatenated.
 *
 * Assumes parcels.current_status holds the DeliveryStatus enum name (e.g. OUT_FOR_DELIVERY).
 */
@Repository
public class JdbcParcelGateway implements ParcelGateway {

    private static final String SELECT = """
            select id, tracking_number, recipient_name, recipient_phone, recipient_address, current_status
            from parcels
            """;

    private static final RowMapper<ParcelSummary> MAPPER = (rs, rowNum) -> new ParcelSummary(
            rs.getLong("id"),
            rs.getString("tracking_number"),
            rs.getString("recipient_name"),
            rs.getString("recipient_phone"),
            rs.getString("recipient_address"),
            toStatus(rs.getString("current_status")));

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcParcelGateway(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ParcelSummary> findById(Long parcelId) {
        return jdbc.query(SELECT + " where id = :id", Map.of("id", parcelId), MAPPER)
                .stream().findFirst();
    }

    @Override
    public List<ParcelSummary> findByIds(Collection<Long> parcelIds) {
        if (parcelIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query(SELECT + " where id in (:ids)", Map.of("ids", parcelIds), MAPPER);
    }

    @Override
    public List<ParcelSummary> findAssignable() {
        return jdbc.query(SELECT + " where current_status <> :delivered order by created_at desc, id desc",
                Map.of("delivered", DeliveryStatus.DELIVERED.name()), MAPPER);
    }

    @Override
    public void updateStatus(Long parcelId, DeliveryStatus status) {
        int updated = jdbc.update("update parcels set current_status = :status where id = :id",
                new MapSqlParameterSource()
                        .addValue("status", status.name())
                        .addValue("id", parcelId));
        if (updated != 1) {
            throw new IllegalStateException("Parcel status update affected " + updated + " rows");
        }
    }

    @Override
    public Map<DeliveryStatus, Long> countByStatus() {
        Map<DeliveryStatus, Long> counts = new EnumMap<>(DeliveryStatus.class);
        for (DeliveryStatus status : DeliveryStatus.values()) {
            counts.put(status, 0L);
        }
        jdbc.query("select current_status, count(*) as total from parcels group by current_status",
                (RowCallbackHandler) rs -> {
                    Optional<DeliveryStatus> status = DeliveryStatus.parse(rs.getString("current_status"));
                    if (status.isPresent()) {
                        counts.put(status.get(), rs.getLong("total"));
                    }
                });
        return counts;
    }

    private static DeliveryStatus toStatus(String value) {
        return DeliveryStatus.parse(value)
                .orElseThrow(() -> new IllegalStateException("Unknown parcel status in database"));
    }
}
