package com.parceltrack.delivery;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryStatusTest {

    @Test
    void onlyTheFiveExactStatusNamesParse() {
        assertThat(DeliveryStatus.parse("DELIVERED")).contains(DeliveryStatus.DELIVERED);
        assertThat(DeliveryStatus.parse("OUT_FOR_DELIVERY")).contains(DeliveryStatus.OUT_FOR_DELIVERY);

        for (String bad : new String[]{null, "", "delivered", " DELIVERED", "LOST", "Out for Delivery",
                "DELIVERED' OR '1'='1", "<script>alert(1)</script>", "1"}) {
            assertThat(DeliveryStatus.parse(bad)).as("'%s'", bad).isEmpty();
        }
    }

    @Test
    void riderTransitionsFollowTheDeliveryFlow() {
        assertThat(DeliveryStatus.ASSIGNED.canRiderMoveTo(DeliveryStatus.OUT_FOR_DELIVERY)).isTrue();
        assertThat(DeliveryStatus.OUT_FOR_DELIVERY.canRiderMoveTo(DeliveryStatus.DELIVERED)).isTrue();
        assertThat(DeliveryStatus.OUT_FOR_DELIVERY.canRiderMoveTo(DeliveryStatus.FAILED)).isTrue();
        assertThat(DeliveryStatus.FAILED.canRiderMoveTo(DeliveryStatus.OUT_FOR_DELIVERY)).isTrue();

        assertThat(DeliveryStatus.ASSIGNED.canRiderMoveTo(DeliveryStatus.DELIVERED)).isFalse();
        assertThat(DeliveryStatus.DELIVERED.riderTransitions()).isEmpty();
        assertThat(DeliveryStatus.REGISTERED.riderTransitions()).isEmpty();
        assertThat(DeliveryStatus.OUT_FOR_DELIVERY.canRiderMoveTo(DeliveryStatus.REGISTERED)).isFalse();
    }

    @Test
    void deliveredParcelsCannotBeReassigned() {
        assertThat(DeliveryStatus.DELIVERED.isAssignable()).isFalse();
        assertThat(DeliveryStatus.REGISTERED.isAssignable()).isTrue();
        assertThat(DeliveryStatus.FAILED.isAssignable()).isTrue();
    }
}
