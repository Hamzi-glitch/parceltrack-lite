package com.parceltrack.parcel;

import com.parceltrack.user.User;
import com.parceltrack.user.UserRepository;
import com.parceltrack.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * End-to-end tests for M2 against an in-memory database, through the real security filter chain.
 * Covers the M2 test cases from the master plan. Uses synthetic data only.
 */
@SpringBootTest(properties = "app.upload-dir=target/test-uploads")
@AutoConfigureMockMvc
class ParcelModuleIntegrationTest {

    private static final String SELLER = "seller@parceltrack.local";
    private static final String SELLER2 = "seller2@parceltrack.local";
    private static final String RIDER = "rider@parceltrack.local";

    private static final String NAME = "Test Recipient";
    private static final String PHONE = "0123456789";
    private static final String ADDRESS = "1 Test Street, Test Town";
    private static final String WEIGHT = "1.50";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired UserService userService;
    @Autowired ParcelService parcelService;

    private Long sellerId;
    private Long seller2Id;

    @BeforeEach
    void setUp() {
        // Start every test from an empty parcel set (children first, because of the foreign keys).
        jdbc.update("delete from delivery_proofs");
        jdbc.update("delete from delivery_history");
        jdbc.update("delete from parcel_assignments");
        jdbc.update("delete from parcels");
        if (!users.existsByEmailIgnoreCase(SELLER2)) {
            userService.registerSeller("Second Seller", SELLER2, "Seller2Pass12345");
        }
        sellerId = users.findByEmailIgnoreCase(SELLER).map(User::getId).orElseThrow();
        seller2Id = users.findByEmailIgnoreCase(SELLER2).map(User::getId).orElseThrow();
    }

    private static RequestPostProcessor seller() { return user(SELLER).roles("SELLER"); }
    private static RequestPostProcessor seller2() { return user(SELLER2).roles("SELLER"); }
    private static RequestPostProcessor rider() { return user(RIDER).roles("RIDER"); }

    private ResultActions register(RequestPostProcessor who, String name, String phone, String address, String weight)
            throws Exception {
        return mvc.perform(post("/seller/parcels")
                .param("recipientName", name)
                .param("recipientPhone", phone)
                .param("recipientAddress", address)
                .param("weight", weight)
                .with(who).with(csrf()));
    }

    private long parcelCount() {
        return jdbc.queryForObject("select count(*) from parcels", Long.class);
    }

    private Map<String, Object> onlyParcelOf(long ownerId) {
        return jdbc.queryForMap("select id, tracking_number, current_status from parcels where seller_id = ?", ownerId);
    }

    private String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    // ======================================================== registration

    @Test
    void sellerRegistersAParcelAndGetsATrackingNumberAndAFirstHistoryRow() throws Exception {
        register(seller(), NAME, PHONE, ADDRESS, WEIGHT)
                .andExpect(status().is3xxRedirection());

        assertThat(parcelCount()).isEqualTo(1);
        Map<String, Object> row = onlyParcelOf(sellerId);
        assertThat((String) row.get("tracking_number"))
                .matches("^PT-[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}$");
        assertThat(row.get("current_status")).isEqualTo("REGISTERED");

        Long parcelId = ((Number) row.get("id")).longValue();
        assertThat(jdbc.queryForObject(
                "select count(*) from delivery_history where parcel_id = ? and status = 'REGISTERED' and changed_by = ?",
                Long.class, parcelId, sellerId)).isEqualTo(1);
    }

    @Test
    void twoParcelsGetDifferentTrackingNumbers() throws Exception {
        register(seller(), NAME, PHONE, ADDRESS, WEIGHT).andExpect(status().is3xxRedirection());
        register(seller(), NAME, PHONE, ADDRESS, WEIGHT).andExpect(status().is3xxRedirection());

        assertThat(jdbc.queryForObject("select count(distinct tracking_number) from parcels", Long.class)).isEqualTo(2);
    }

    static Stream<Arguments> invalidRegistrations() {
        return Stream.of(
                Arguments.of("blank name", "  ", PHONE, ADDRESS, WEIGHT),
                Arguments.of("letters in phone", NAME, "abcdefghij", ADDRESS, WEIGHT),
                Arguments.of("phone too short", NAME, "12345", ADDRESS, WEIGHT),
                Arguments.of("negative weight", NAME, PHONE, ADDRESS, "-5"),
                Arguments.of("zero weight", NAME, PHONE, ADDRESS, "0"),
                Arguments.of("non-numeric weight", NAME, PHONE, ADDRESS, "heavy"),
                Arguments.of("weight too large", NAME, PHONE, ADDRESS, "1000"),
                Arguments.of("too many decimals", NAME, PHONE, ADDRESS, "1.234"),
                Arguments.of("overlong address", NAME, PHONE, "a".repeat(Parcel.MAX_ADDRESS + 1), WEIGHT),
                Arguments.of("overlong name", "n".repeat(Parcel.MAX_NAME + 1), PHONE, ADDRESS, WEIGHT));
    }

    @ParameterizedTest(name = "{0} is rejected")
    @MethodSource("invalidRegistrations")
    void invalidInputIsRejectedByTheServerAndNothingIsSaved(String label, String name, String phone,
                                                            String address, String weight) throws Exception {
        register(seller(), name, phone, address, weight)
                .andExpect(status().isOk())
                .andExpect(view().name("seller/parcel-form"));

        assertThat(parcelCount()).isZero();
    }

    @Test
    void nonNumericWeightShowsAFriendlyMessageNotATechnicalOne() throws Exception {
        String html = body(register(seller(), NAME, PHONE, ADDRESS, "heavy"));

        assertThat(html).contains("Weight must be a number");
        assertThat(html).doesNotContain("Failed to convert").doesNotContain("NumberFormatException");
    }

    // ======================================================== XSS

    @Test
    void scriptInTheRecipientNameAndAddressIsShownAsTextNotRun() throws Exception {
        register(seller(), "<script>alert(1)</script>", PHONE, "<img src=x onerror=alert(2)>", WEIGHT)
                .andExpect(status().is3xxRedirection());
        long parcelId = ((Number) onlyParcelOf(sellerId).get("id")).longValue();

        String html = body(mvc.perform(get("/seller/parcels/{id}", parcelId).with(seller()))
                .andExpect(status().isOk()));

        assertThat(html).contains("&lt;script&gt;alert(1)&lt;/script&gt;");
        assertThat(html).doesNotContain("<script>alert(1)");
        assertThat(html).doesNotContain("<img src=x");

        String list = body(mvc.perform(get("/seller/parcels").with(seller())).andExpect(status().isOk()));
        assertThat(list).doesNotContain("<script>alert(1)");
    }

    // ======================================================== ownership (IDOR) and search

    @Test
    void sellerCanOpenTheirOwnParcel() throws Exception {
        register(seller(), NAME, PHONE, ADDRESS, WEIGHT);
        long parcelId = ((Number) onlyParcelOf(sellerId).get("id")).longValue();

        mvc.perform(get("/seller/parcels/{id}", parcelId).with(seller()))
                .andExpect(status().isOk())
                .andExpect(view().name("seller/parcel-detail"));
    }

    @Test
    void sellerChangingTheParcelIdCannotReachAnotherSellersParcel() throws Exception {
        register(seller2(), "Other Sellers Secret", PHONE, ADDRESS, WEIGHT);
        long othersParcelId = ((Number) onlyParcelOf(seller2Id).get("id")).longValue();

        // Same generic 404 as a parcel that does not exist, so the id cannot be probed.
        String forbiddenLooking = body(mvc.perform(get("/seller/parcels/{id}", othersParcelId).with(seller()))
                .andExpect(status().isNotFound()));
        String missing = body(mvc.perform(get("/seller/parcels/{id}", othersParcelId + 1000).with(seller()))
                .andExpect(status().isNotFound()));

        assertThat(forbiddenLooking).doesNotContain("Other Sellers Secret");
        assertThat(stripCsrf(forbiddenLooking)).isEqualTo(stripCsrf(missing));
    }
	private static String stripCsrf(String html) {
    return html.replaceAll("name=\"_csrf\"\\s+value=\"[^\"]*\"", "name=\"_csrf\" value=\"X\"");
}
    @Test
    void nonNumericParcelIdIsRejectedWithoutReachingTheDatabase() throws Exception {
        mvc.perform(get("/seller/parcels/{id}", "1 OR 1=1").with(seller()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void searchOnlyFindsTheSellersOwnParcels() throws Exception {
        register(seller(), "Mine Alpha", PHONE, ADDRESS, WEIGHT);
        register(seller2(), "Other Sellers Secret", PHONE, ADDRESS, WEIGHT);

        String mine = body(mvc.perform(get("/seller/parcels").param("q", "alpha").with(seller()))
                .andExpect(status().isOk()));
        assertThat(mine).contains("Mine Alpha");

        String others = body(mvc.perform(get("/seller/parcels").param("q", "secret").with(seller()))
                .andExpect(status().isOk()));
        assertThat(others).doesNotContain("Other Sellers Secret");
    }

    @Test
    void sqlInjectionInSearchReturnsNothingExtraAndNoError() throws Exception {
        register(seller(), "Mine Alpha", PHONE, ADDRESS, WEIGHT);
        register(seller2(), "Other Sellers Secret", PHONE, ADDRESS, WEIGHT);

        for (String attack : new String[]{"' OR '1'='1", "'; DROP TABLE parcels; --", "%", "_"}) {
            String html = body(mvc.perform(get("/seller/parcels").param("q", attack).with(seller()))
                    .andExpect(status().isOk()));
            assertThat(html).doesNotContain("Other Sellers Secret");
            assertThat(html).doesNotContain("Mine Alpha");
        }
        assertThat(parcelCount()).isEqualTo(2); // the table is still there
    }

    @Test
    void overlongSearchTextIsHandledSafely() throws Exception {
        mvc.perform(get("/seller/parcels").param("q", "a".repeat(5_000)).with(seller()))
                .andExpect(status().isOk());
    }

    // ======================================================== public tracking

    @Test
    void publicTrackingShowsStatusHistoryButNeverRecipientDetails() throws Exception {
        register(seller(), "Hidden Recipient", "0198765432", "99 Secret Lane", WEIGHT);
        String trackingNumber = (String) onlyParcelOf(sellerId).get("tracking_number");

        // No login: no .with(user(...)) here.
        String html = body(mvc.perform(get("/track").param("number", trackingNumber))
                .andExpect(status().isOk()));

        assertThat(html).contains(trackingNumber).contains("Registered");
        assertThat(html).doesNotContain("Hidden Recipient")
                .doesNotContain("0198765432")
                .doesNotContain("99 Secret Lane");
    }

    @Test
    void trackingNumberCanBeTypedInLowerCase() throws Exception {
        register(seller(), NAME, PHONE, ADDRESS, WEIGHT);
        String trackingNumber = (String) onlyParcelOf(sellerId).get("tracking_number");

        mvc.perform(get("/track").param("number", trackingNumber.toLowerCase()))
                .andExpect(status().isOk());
    }

    @Test
    void unknownMalformedAndInjectedTrackingNumbersAllGetTheSameGenericAnswer() throws Exception {
        register(seller(), NAME, PHONE, ADDRESS, WEIGHT);

        String unknown = body(mvc.perform(get("/track").param("number", "PT-AAAA-AAAA-AAAA"))
                .andExpect(status().isNotFound()));
        String sequentialId = body(mvc.perform(get("/track").param("number", "1"))
                .andExpect(status().isNotFound()));
        String injected = body(mvc.perform(get("/track").param("number", "' OR '1'='1"))
                .andExpect(status().isNotFound()));
        String overlong = body(mvc.perform(get("/track").param("number", "A".repeat(5_000)))
                .andExpect(status().isNotFound()));

        assertThat(unknown).contains("We could not find a parcel");
        assertThat(sequentialId).isEqualTo(unknown);
        assertThat(injected).isEqualTo(unknown);
        assertThat(overlong).isEqualTo(unknown);
    }

    @Test
    void trackingPageWithoutANumberJustShowsTheForm() throws Exception {
        mvc.perform(get("/track")).andExpect(status().isOk());
    }

    // ======================================================== access control

    @Test
    void anonymousVisitorIsSentToLoginFromSellerPages() throws Exception {
        mvc.perform(get("/seller/parcels")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/seller/parcels/new")).andExpect(status().is3xxRedirection());
        mvc.perform(post("/seller/parcels").param("recipientName", NAME).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(parcelCount()).isZero();
    }

    @Test
    void riderCannotUseSellerPages() throws Exception {
        mvc.perform(get("/seller/parcels").with(rider())).andExpect(status().isForbidden());
        register(rider(), NAME, PHONE, ADDRESS, WEIGHT).andExpect(status().isForbidden());
        assertThat(parcelCount()).isZero();
    }

    @Test
    void registrationWithoutACsrfTokenIsRefused() throws Exception {
        mvc.perform(post("/seller/parcels")
                        .param("recipientName", NAME).param("recipientPhone", PHONE)
                        .param("recipientAddress", ADDRESS).param("weight", WEIGHT)
                        .with(seller()))
                .andExpect(status().isForbidden());
        assertThat(parcelCount()).isZero();
    }

    @Test
    void serviceRefusesCallsWithoutALoggedInUser() {
        // Defence in depth: the service is protected even if a future endpoint forgets a URL rule.
        assertThatThrownBy(() -> parcelService.listOwn(SELLER, null))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }
}
