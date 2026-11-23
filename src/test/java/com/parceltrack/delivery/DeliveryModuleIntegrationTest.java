package com.parceltrack.delivery;

import com.parceltrack.user.User;
import com.parceltrack.user.UserRepository;
import com.parceltrack.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests for M3 against an in-memory database, through the real security filter chain.
 * Covers the M3 test cases from the master plan.
 */
@SpringBootTest(properties = "app.upload-dir=target/test-uploads")
@AutoConfigureMockMvc
@Sql("/m3/parcels-h2.sql")
class DeliveryModuleIntegrationTest {

    private static final String ADMIN = "admin@parceltrack.local";
    private static final String RIDER = "rider@parceltrack.local";
    private static final String RIDER2 = "rider2@parceltrack.local";
    private static final String SELLER = "seller@parceltrack.local";
    private static final Path UPLOADS = Path.of("target/test-uploads");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired UserService userService;
    @Autowired DeliveryService deliveryService;

    @MockitoSpyBean DeliveryHistoryRepository historyRepository;
    @MockitoSpyBean DeliveryProofRepository proofRepository;
    @MockitoSpyBean FileStorageService fileStorage;

    private Long riderId;
    private Long rider2Id;

    @BeforeEach
    void setUp() {
        if (!users.existsByEmailIgnoreCase(RIDER2)) {
            userService.createRider("Second Rider", RIDER2, "Rider2Pass12345");
        }
        riderId = users.findByEmailIgnoreCase(RIDER).map(User::getId).orElseThrow();
        rider2Id = users.findByEmailIgnoreCase(RIDER2).map(User::getId).orElseThrow();
    }

    private static RequestPostProcessor admin() { return user(ADMIN).roles("ADMIN"); }
    private static RequestPostProcessor rider() { return user(RIDER).roles("RIDER"); }
    private static RequestPostProcessor rider2() { return user(RIDER2).roles("RIDER"); }
    private static RequestPostProcessor seller() { return user(SELLER).roles("SELLER"); }

    private String statusOf(long parcelId) {
        return jdbc.queryForObject("select current_status from parcels where id = ?", String.class, parcelId);
    }

    private int historyCount(long parcelId) {
        return jdbc.queryForObject("select count(*) from delivery_history where parcel_id = ?", Integer.class, parcelId);
    }

    private int proofCount(long parcelId) {
        return jdbc.queryForObject("select count(*) from delivery_proofs where parcel_id = ?", Integer.class, parcelId);
    }

    private void assign(long parcelId, long toRider) throws Exception {
        mvc.perform(post("/admin/parcels/{id}/assign", parcelId).param("riderId", String.valueOf(toRider))
                        .with(admin()).with(csrf()))
                .andExpect(redirectedUrl("/admin/parcels"))
                .andExpect(flash().attribute("success", "Parcel assigned."));
    }

    private void riderSets(long parcelId, String newStatus, String remarks) throws Exception {
        mvc.perform(post("/rider/parcels/{id}/status", parcelId).param("status", newStatus)
                        .param("remarks", remarks == null ? "" : remarks)
                        .with(rider()).with(csrf()))
                .andExpect(redirectedUrl("/rider/parcels/" + parcelId))
                .andExpect(flash().attributeExists("success"));
    }

    private void deliverToRider(long parcelId) throws Exception {
        assign(parcelId, riderId);
        riderSets(parcelId, "OUT_FOR_DELIVERY", null);
        riderSets(parcelId, "DELIVERED", null);
    }

    private long uploadedFileCount() throws IOException {
        if (!Files.exists(UPLOADS)) {
            return 0;
        }
        try (Stream<Path> files = Files.list(UPLOADS)) {
            return files.count();
        }
    }

    // ======================================================== assignment

    @Test
    void adminAssignsParcelAndStatusPlusHistoryAreSaved() throws Exception {
        assign(101, riderId);

        assertThat(statusOf(101)).isEqualTo("ASSIGNED");
        assertThat(historyCount(101)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select rider_id from parcel_assignments where parcel_id = 101", Long.class))
                .isEqualTo(riderId);
    }

    @Test
    void riderCannotAssignParcels() throws Exception {
        mvc.perform(post("/admin/parcels/101/assign").param("riderId", String.valueOf(riderId))
                        .with(rider()).with(csrf()))
                .andExpect(status().isForbidden());
        assertThat(statusOf(101)).isEqualTo("REGISTERED");
    }

    @Test
    void serviceMethodsRefuseCallsWithoutALoggedInUser() {
        // Defence in depth: the service is protected even if a future endpoint forgets a URL rule.
        assertThatThrownBy(() -> deliveryService.statusCounts(RIDER))
                .isInstanceOf(org.springframework.security.authentication.AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    @WithMockUser(username = RIDER, roles = "ADMIN")
    void serviceRoleCheckUsesTheDatabaseRoleNotJustTheSessionRole() {
        // Session claims ADMIN, but the database says this user is a Rider.
        assertThatThrownBy(() -> deliveryService.statusCounts(RIDER))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void cannotAssignToANonRiderOrADeliveredParcel() throws Exception {
        Long sellerId = users.findByEmailIgnoreCase(SELLER).map(User::getId).orElseThrow();
        mvc.perform(post("/admin/parcels/101/assign").param("riderId", String.valueOf(sellerId))
                        .with(admin()).with(csrf()))
                .andExpect(flash().attribute("error", "Please choose an active Rider."));

        mvc.perform(post("/admin/parcels/103/assign").param("riderId", String.valueOf(riderId))
                        .with(admin()).with(csrf()))
                .andExpect(flash().attribute("error", "A delivered parcel cannot be reassigned."));

        assertThat(statusOf(101)).isEqualTo("REGISTERED");
        assertThat(statusOf(103)).isEqualTo("DELIVERED");
    }

    @Test
    void assigningAnUnknownParcelGivesGenericNotFound() throws Exception {
        mvc.perform(post("/admin/parcels/999999/assign").param("riderId", String.valueOf(riderId))
                        .with(admin()).with(csrf()))
                .andExpect(status().isNotFound());
    }

    // ======================================================== rider access

    @Test
    void riderSeesOnlyParcelsCurrentlyAssignedToThem() throws Exception {
        assign(101, riderId);
        assign(102, rider2Id);

        mvc.perform(get("/rider/parcels").with(rider()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("PT-TEST-0101")))
                .andExpect(content().string(not(containsString("PT-TEST-0102"))));
    }

    @Test
    void riderCannotOpenOrUpdateAnotherRidersParcel() throws Exception {
        assign(101, riderId);

        mvc.perform(get("/rider/parcels/101").with(rider2())).andExpect(status().isNotFound());
        mvc.perform(post("/rider/parcels/101/status").param("status", "OUT_FOR_DELIVERY")
                        .with(rider2()).with(csrf()))
                .andExpect(status().isNotFound());

        assertThat(statusOf(101)).isEqualTo("ASSIGNED");
    }

    @Test
    void reassignmentRemovesAccessFromThePreviousRider() throws Exception {
        assign(101, riderId);
        assign(101, rider2Id);

        mvc.perform(get("/rider/parcels/101").with(rider())).andExpect(status().isNotFound());
        mvc.perform(get("/rider/parcels/101").with(rider2())).andExpect(status().isOk());
    }

    @Test
    void unassignedAndNonexistentParcelsLookTheSame() throws Exception {
        mvc.perform(get("/rider/parcels/101").with(rider())).andExpect(status().isNotFound());
        mvc.perform(get("/rider/parcels/999999").with(rider())).andExpect(status().isNotFound());
    }

    // ======================================================== status updates

    @Test
    void riderMovesParcelThroughTheFlowAndHistoryKeepsEveryStep() throws Exception {
        deliverToRider(101);

        assertThat(statusOf(101)).isEqualTo("DELIVERED");
        assertThat(jdbc.queryForList("select status from delivery_history where parcel_id = 101 order by id", String.class))
                .containsExactly("ASSIGNED", "OUT_FOR_DELIVERY", "DELIVERED");
        assertThat(jdbc.queryForList("select changed_by from delivery_history where parcel_id = 101 and status <> 'ASSIGNED'", Long.class))
                .containsOnly(riderId);
    }

    @Test
    void invalidStatusValueIsRejected() throws Exception {
        assign(101, riderId);
        for (String bad : new String[]{"HACKED", "delivered", "DELIVERED' OR '1'='1", "<script>x</script>"}) {
            mvc.perform(post("/rider/parcels/101/status").param("status", bad).with(rider()).with(csrf()))
                    .andExpect(flash().attribute("error", "Invalid status value."));
        }
        assertThat(statusOf(101)).isEqualTo("ASSIGNED");
        assertThat(historyCount(101)).isEqualTo(1);
    }

    @Test
    void skippingStepsAndFailingWithoutReasonAreRejected() throws Exception {
        assign(101, riderId);
        mvc.perform(post("/rider/parcels/101/status").param("status", "DELIVERED").with(rider()).with(csrf()))
                .andExpect(flash().attribute("error", containsString("cannot be changed to Delivered")));

        riderSets(101, "OUT_FOR_DELIVERY", null);
        mvc.perform(post("/rider/parcels/101/status").param("status", "FAILED").with(rider()).with(csrf()))
                .andExpect(flash().attribute("error", "Please give a reason when a delivery fails."));

        riderSets(101, "FAILED", "Recipient not home");
        assertThat(statusOf(101)).isEqualTo("FAILED");
    }

    @Test
    void overlongRemarksAreRejected() throws Exception {
        assign(101, riderId);
        mvc.perform(post("/rider/parcels/101/status").param("status", "OUT_FOR_DELIVERY")
                        .param("remarks", "x".repeat(300)).with(rider()).with(csrf()))
                .andExpect(flash().attribute("error", containsString("255 characters")));
        assertThat(statusOf(101)).isEqualTo("ASSIGNED");
    }

    @Test
    void statusAndHistoryRollBackTogetherWhenHistoryInsertFails() throws Exception {
        assign(101, riderId);
        doThrow(new DataAccessResourceFailureException("simulated database failure"))
                .when(historyRepository).save(any(DeliveryHistory.class));

        mvc.perform(post("/rider/parcels/101/status").param("status", "OUT_FOR_DELIVERY").with(rider()).with(csrf()))
                .andExpect(status().is5xxServerError())
                .andExpect(content().string(not(containsString("simulated database failure"))));

        assertThat(statusOf(101)).isEqualTo("ASSIGNED");
        assertThat(historyCount(101)).isEqualTo(1);
    }

    // ======================================================== attacks on the web layer

    @Test
    void sqlInjectionThroughParcelIdIsRejectedAsBadRequest() throws Exception {
        assign(101, riderId);
        mvc.perform(get("/rider/parcels/101 OR 1=1").with(rider())).andExpect(status().isBadRequest());
        mvc.perform(get("/rider/parcels/101;DROP TABLE parcels").with(rider())).andExpect(status().isBadRequest());
        mvc.perform(post("/rider/parcels/101 OR 1=1/status").param("status", "OUT_FOR_DELIVERY")
                        .with(rider()).with(csrf()))
                .andExpect(status().isBadRequest());
        assertThat(statusOf(101)).isEqualTo("ASSIGNED");
    }

    @Test
    void stateChangingRequestsWithoutCsrfTokenAreRefused() throws Exception {
        assign(101, riderId);
        mvc.perform(post("/rider/parcels/101/status").param("status", "OUT_FOR_DELIVERY").with(rider()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/parcels/102/assign").param("riderId", String.valueOf(riderId)).with(admin()))
                .andExpect(status().isForbidden());
        assertThat(statusOf(101)).isEqualTo("ASSIGNED");
        assertThat(statusOf(102)).isEqualTo("REGISTERED");
    }

    @Test
    void recipientDataWithScriptsIsShownAsTextNotRun() throws Exception {
        assign(102, riderId);
        mvc.perform(get("/rider/parcels").with(rider()))
                .andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")))
                .andExpect(content().string(not(containsString("<script>alert(1)</script>"))))
                .andExpect(content().string(not(containsString("<img src=x"))));
    }

    // ======================================================== role checks on pages

    @Test
    void adminDashboardIsAdminOnly() throws Exception {
        mvc.perform(get("/admin/dashboard")).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/admin/dashboard").with(rider())).andExpect(status().isForbidden());
        mvc.perform(get("/admin/dashboard").with(seller())).andExpect(status().isForbidden());
        mvc.perform(get("/admin/parcels").with(rider())).andExpect(status().isForbidden());
    }

    @Test
    void riderPagesAreRiderOnly() throws Exception {
        mvc.perform(get("/rider/parcels")).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/rider/parcels").with(seller())).andExpect(status().isForbidden());
        mvc.perform(get("/rider/parcels").with(admin())).andExpect(status().isForbidden());
    }

    // ======================================================== dashboard counts

    @Test
    void dashboardCountsComeFromTheDatabase() throws Exception {
        mvc.perform(get("/admin/dashboard").with(admin()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("totalParcels", 3L))
                .andExpect(model().attribute("statusCounts", Map.of(
                        DeliveryStatus.REGISTERED, 2L, DeliveryStatus.ASSIGNED, 0L,
                        DeliveryStatus.OUT_FOR_DELIVERY, 0L, DeliveryStatus.DELIVERED, 1L,
                        DeliveryStatus.FAILED, 0L)));

        assign(101, riderId);

        mvc.perform(get("/admin/dashboard").with(admin()))
                .andExpect(model().attribute("statusCounts", Map.of(
                        DeliveryStatus.REGISTERED, 1L, DeliveryStatus.ASSIGNED, 1L,
                        DeliveryStatus.OUT_FOR_DELIVERY, 0L, DeliveryStatus.DELIVERED, 1L,
                        DeliveryStatus.FAILED, 0L)));
    }

    @Test
    void adminAssignmentPageListsOnlyUndeliveredParcels() throws Exception {
        mvc.perform(get("/admin/parcels").with(admin()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("PT-TEST-0101")))
                .andExpect(content().string(not(containsString("PT-TEST-0103"))))
                // The assignment page does not need recipient details, so it must not show them.
                .andExpect(content().string(not(containsString("Test Recipient One"))));
    }

    // ======================================================== proof of delivery

    @Test
    void riderUploadsOneProofAfterDeliveryAndOnlyAuthorizedUsersCanView() throws Exception {
        deliverToRider(101);
        MockMultipartFile photo = new MockMultipartFile("photo", "door.png", "image/png", TestImages.png(30, 30));

        mvc.perform(multipart("/rider/parcels/101/proof").file(photo).with(rider()).with(csrf()))
                .andExpect(flash().attribute("success", "Proof-of-delivery photo uploaded."));
        assertThat(proofCount(101)).isEqualTo(1);
        String stored = jdbc.queryForObject("select stored_filename from delivery_proofs where parcel_id = 101", String.class);
        assertThat(stored).doesNotContain("door").endsWith(".png");

        mvc.perform(get("/rider/parcels/101/proof").with(rider()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        mvc.perform(get("/admin/parcels/101/proof").with(admin())).andExpect(status().isOk());
        mvc.perform(get("/rider/parcels/101/proof").with(rider2())).andExpect(status().isNotFound());
        mvc.perform(get("/rider/parcels/101/proof").with(seller())).andExpect(status().isForbidden());

        // Only one proof per parcel.
        mvc.perform(multipart("/rider/parcels/101/proof").file(photo).with(rider()).with(csrf()))
                .andExpect(flash().attribute("error", containsString("already been uploaded")));
        assertThat(proofCount(101)).isEqualTo(1);
    }

    @Test
    void maliciousUploadsAreRejectedThroughTheWebLayer() throws Exception {
        deliverToRider(101);
        long before = uploadedFileCount();

        MockMultipartFile[] attacks = {
                new MockMultipartFile("photo", "shell.php", "application/x-php", "<?php system($_GET['c']); ?>".getBytes()),
                new MockMultipartFile("photo", "shell.php.jpg", "image/jpeg", TestImages.jpg(10, 10)),
                new MockMultipartFile("photo", "renamed.png", "image/png", "MZ this is really an exe".getBytes()),
                new MockMultipartFile("photo", "broken.png", "image/png", java.util.Arrays.copyOf(TestImages.png(40, 40), 40)),
        };
        for (MockMultipartFile attack : attacks) {
            mvc.perform(multipart("/rider/parcels/101/proof").file(attack).with(rider()).with(csrf()))
                    .andExpect(redirectedUrl("/rider/parcels/101"))
                    .andExpect(flash().attributeExists("error"));
        }
        assertThat(proofCount(101)).isZero();
        assertThat(uploadedFileCount()).isEqualTo(before);
    }

    @Test
    void cannotUploadProofBeforeDeliveryOrForAnotherRidersParcel() throws Exception {
        assign(101, riderId);
        MockMultipartFile photo = new MockMultipartFile("photo", "door.png", "image/png", TestImages.png(10, 10));

        mvc.perform(multipart("/rider/parcels/101/proof").file(photo).with(rider()).with(csrf()))
                .andExpect(flash().attribute("error", containsString("before uploading proof")));
        mvc.perform(multipart("/rider/parcels/101/proof").file(photo).with(rider2()).with(csrf()))
                .andExpect(status().isNotFound());
        assertThat(proofCount(101)).isZero();
    }

    @Test
    void fileStorageFailureShowsGenericErrorAndSavesNothing() throws Exception {
        deliverToRider(101);
        doThrow(new FileStorageException("disk full", null)).when(fileStorage).storeProofPhoto(any());

        mvc.perform(multipart("/rider/parcels/101/proof")
                        .file(new MockMultipartFile("photo", "door.png", "image/png", TestImages.png(10, 10)))
                        .with(rider()).with(csrf()))
                .andExpect(flash().attribute("error", "The photo could not be saved. Please try again later."));
        assertThat(proofCount(101)).isZero();
    }

    @Test
    void databaseFailureAfterFileSaveDeletesTheFile() throws Exception {
        deliverToRider(101);
        long before = uploadedFileCount();
        doThrow(new DataAccessResourceFailureException("simulated database failure"))
                .when(proofRepository).saveAndFlush(any(DeliveryProof.class));

        mvc.perform(multipart("/rider/parcels/101/proof")
                        .file(new MockMultipartFile("photo", "door.png", "image/png", TestImages.png(10, 10)))
                        .with(rider()).with(csrf()))
                .andExpect(status().is5xxServerError());

        assertThat(proofCount(101)).isZero();
        assertThat(uploadedFileCount()).isEqualTo(before);
    }
}
