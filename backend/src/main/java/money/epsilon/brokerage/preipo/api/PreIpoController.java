package money.epsilon.brokerage.preipo.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import money.epsilon.brokerage.platform.security.Roles;
import money.epsilon.brokerage.platform.importing.UploadedFile;
import money.epsilon.brokerage.preipo.domain.PreIpoEntry;
import money.epsilon.brokerage.preipo.domain.PreIpoEntry.Details;
import money.epsilon.brokerage.preipo.service.PreIpoPriceService;
import money.epsilon.brokerage.preipo.service.PreIpoPriceService.BoardRow;
import money.epsilon.brokerage.preipo.service.PreIpoPriceService.PriceView;
import money.epsilon.brokerage.preipo.service.PreIpoPriceService.ScripInput;
import money.epsilon.brokerage.preipo.service.PreIpoPriceService.ScripView;
import money.epsilon.brokerage.preipo.service.PreIpoPriceService.UploadResult;
import money.epsilon.brokerage.preipo.service.PreIpoService;
import money.epsilon.brokerage.preipo.service.PreIpoService.PreIpoView;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** The Pre-IPO screen: deals (buy / buyback) and Available IPOs (scrips and each vendor's dated prices). */
@RestController
@RequestMapping("/api/preipo")
class PreIpoController {

    private final PreIpoService deals;
    private final PreIpoPriceService prices;

    PreIpoController(PreIpoService deals, PreIpoPriceService prices) {
        this.deals = deals;
        this.prices = prices;
    }

    // ---- deals -------------------------------------------------------------------------------------------------------

    @GetMapping
    @PreAuthorize(Roles.MASTER_READ)
    List<PreIpoView> list(@RequestParam(required = false) YearMonth from, @RequestParam(required = false) YearMonth to,
                          @RequestParam(required = false) String q, @RequestParam(required = false) PreIpoEntry.Kind kind,
                          @RequestParam(required = false) Long rmId, @RequestParam(required = false) Long partnerId,
                          @RequestParam(required = false) Long platformId, @RequestParam(required = false) String payment,
                          @RequestParam(defaultValue = "false") boolean includeCancelled) {
        return deals.search(new PreIpoService.Filter(from, to, q, kind, rmId, partnerId, platformId, billing(payment), includeCancelled));
    }

    /** The shared list filter sends other products' payment statuses too: anything that is not a billing status means "any". */
    private static PreIpoEntry.BillingStatus billing(String payment) {
        return payment == null ? null : Arrays.stream(PreIpoEntry.BillingStatus.values()).filter(b -> b.name().equals(payment)).findFirst().orElse(null);
    }

    @GetMapping("/{id}")
    @PreAuthorize(Roles.MASTER_READ)
    PreIpoView get(@PathVariable long id) {
        return deals.get(id);
    }

    @GetMapping("/alerts")
    @PreAuthorize(Roles.MASTER_READ)
    Map<String, Long> alerts() {
        return Map.of("pendingPayments", deals.pendingPayments());
    }

    @GetMapping("/export.xlsx")
    @PreAuthorize(Roles.MASTER_READ)
    ResponseEntity<byte[]> export(@RequestParam(required = false) YearMonth from, @RequestParam(required = false) YearMonth to,
                                  @RequestParam(required = false) String q, @RequestParam(required = false) PreIpoEntry.Kind kind,
                                  @RequestParam(required = false) Long rmId, @RequestParam(required = false) Long partnerId,
                                  @RequestParam(required = false) Long platformId, @RequestParam(required = false) String payment) {
        String name = from == null && to == null ? "all" : (from == null ? "" : from.toString()) + "_to_" + (to == null ? "" : to.toString());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"PRE_IPO_" + name + ".xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(deals.export(new PreIpoService.Filter(from, to, q, kind, rmId, partnerId, platformId, billing(payment), false)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Roles.MASTER_WRITE)
    PreIpoView create(@Valid @RequestBody DealRequest req) {
        return deals.create(req.clientId(), req.newClientName(), req.newClientPan(), req.details());
    }

    @PutMapping("/{id}")
    @PreAuthorize(Roles.MASTER_WRITE)
    PreIpoView update(@PathVariable long id, @Valid @RequestBody DealRequest req) {
        return deals.update(id, req.details());
    }

    @PostMapping("/{id}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Roles.MASTER_WRITE)
    void cancel(@PathVariable long id, @Valid @RequestBody CancelRequest req) {
        deals.cancel(id, req.reason());
    }

    record CancelRequest(@Size(max = 500) String reason) {
    }

    /** Client: {@code clientId} of an existing client, or a new client's name and PAN (add only). */
    record DealRequest(Long clientId, @Size(max = 300) String newClientName, @Size(max = 20) String newClientPan,
                       @NotNull PreIpoEntry.Kind kind, @NotNull LocalDate month, @NotNull Long rmId, Long partnerId, BigDecimal sharingPercent,
                       @Size(max = 20) String mobile, @Size(max = 200) String email, @Size(max = 200) String dpName,
                       @Size(max = 40) String dpId, @Size(max = 40) String dematClientId, @NotNull Long scripId, @NotNull Long vendorId,
                       LocalDate dealDate, BigDecimal quantity, BigDecimal clientPrice, BigDecimal vendorPrice, Boolean tdsApplied,
                       BigDecimal tdsPercent, PreIpoEntry.PortalStatus portalStatus, PreIpoEntry.BillingStatus billingStatus,
                       PreIpoEntry.DealSheet dealSheet, @Size(max = 1000) String remarks) {
        Details details() {
            return new Details(kind, month, rmId, partnerId, sharingPercent, mobile, email, dpName, dpId, dematClientId, scripId, vendorId,
                    dealDate, quantity, clientPrice, vendorPrice, Boolean.TRUE.equals(tdsApplied), tdsPercent, portalStatus, billingStatus,
                    dealSheet, remarks);
        }
    }

    // ---- Available IPOs ----------------------------------------------------------------------------------------------

    @GetMapping("/scrips")
    @PreAuthorize(Roles.MASTER_READ)
    List<ScripView> scrips() {
        return prices.scrips();
    }

    @PostMapping("/scrips")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Roles.MASTER_WRITE)
    ScripView createScrip(@Valid @RequestBody ScripRequest req) {
        return prices.createScrip(req.input());
    }

    @PutMapping("/scrips/{id}")
    @PreAuthorize(Roles.MASTER_WRITE)
    ScripView updateScrip(@PathVariable long id, @Valid @RequestBody ScripRequest req) {
        return prices.updateScrip(id, req.input());
    }

    record ScripRequest(@Size(max = 300) String name, @Size(max = 20) String isin, BigDecimal faceValue, Integer lotSize, Boolean active,
                        @Size(max = 500) String remarks) {
        ScripInput input() {
            return new ScripInput(name, isin, faceValue, lotSize, active, remarks);
        }
    }

    /** Every active scrip with each vendor's latest price on {@code date} (today when not given). */
    @GetMapping("/prices")
    @PreAuthorize(Roles.MASTER_READ)
    List<BoardRow> board(@RequestParam(required = false) LocalDate date) {
        return prices.board(date == null ? LocalDate.now() : date);
    }

    @GetMapping("/scrips/{id}/prices")
    @PreAuthorize(Roles.MASTER_READ)
    List<PriceView> history(@PathVariable long id) {
        return prices.history(id);
    }

    @PostMapping("/prices")
    @PreAuthorize(Roles.MASTER_WRITE)
    PriceView setPrice(@Valid @RequestBody PriceRequest req) {
        return prices.setPrice(req.scripId(), req.vendorId(), req.asOf(), req.basePrice(), req.markupPercent());
    }

    record PriceRequest(@NotNull Long scripId, @NotNull Long vendorId, @NotNull LocalDate asOf, BigDecimal basePrice, BigDecimal markupPercent) {
    }

    @PostMapping("/prices/upload")
    @PreAuthorize(Roles.MASTER_WRITE)
    UploadResult upload(@RequestParam("file") MultipartFile file, @RequestParam("asOf") LocalDate asOf,
                        @RequestParam(required = false) BigDecimal markupPercent) throws IOException {
        UploadedFile f = UploadedFile.of(file, "pre-ipo-prices.xlsx", "the Pre-IPO price sheet");
        return prices.upload(f.content(), f.name(), asOf, markupPercent);
    }
}
