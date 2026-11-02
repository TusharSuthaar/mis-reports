package money.epsilon.brokerage.equity.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.time.YearMonth;
import java.util.List;
import money.epsilon.brokerage.equity.service.EquityService;
import money.epsilon.brokerage.equity.service.EquityService.ClientMonth;
import money.epsilon.brokerage.equity.service.EquityService.TradeView;
import money.epsilon.brokerage.equity.service.EquityService.UploadResult;
import money.epsilon.brokerage.equity.service.EquityService.UploadView;
import money.epsilon.brokerage.platform.importing.UploadedFile;
import money.epsilon.brokerage.platform.security.Roles;
import money.epsilon.brokerage.platform.web.ProductListFilters;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Equity brokerage (R-89): the broker's monthly report upload, and the Equity tab of Product entries. */
@RestController
@RequestMapping("/api/equity")
class EquityController {

    private final EquityService service;

    EquityController(EquityService service) {
        this.service = service;
    }

    @PostMapping("/upload")
    @PreAuthorize(Roles.MASTER_WRITE)
    UploadResult upload(@RequestParam("file") MultipartFile file) throws IOException {
        UploadedFile f = UploadedFile.of(file, "Equity Source.xlsx", "the broker's Brokerage Due report");
        return service.upload(f.content(), f.name());
    }

    @GetMapping("/uploads")
    @PreAuthorize(Roles.MASTER_READ)
    List<UploadView> uploads() {
        return service.uploads();
    }

    /** Client-months; the shared product-list filters (payment, platform…) do not apply to equity and are ignored. */
    @ProductListFilters
    @GetMapping
    @PreAuthorize(Roles.MASTER_READ)
    List<ClientMonth> list(@RequestParam(required = false) YearMonth from, @RequestParam(required = false) YearMonth to,
                           @RequestParam(required = false) String q, @RequestParam(required = false) Long rmId) {
        return service.clientMonths(new EquityService.Filter(from, to, q, rmId));
    }

    /** The month's party codes found by name only, waiting for a confirmation (R-100). */
    @GetMapping("/name-matches")
    @PreAuthorize(Roles.MASTER_READ)
    List<EquityService.NameMatch> nameMatches(@RequestParam YearMonth month) {
        return service.check(month).nameMatches();
    }

    public record ConfirmName(@NotNull YearMonth month, @NotBlank String partyCode, @NotNull Long clientId) {
    }

    /** Confirms the client suggested for a party code found by name, or gives another (R-100). */
    @PostMapping("/name-matches")
    @PreAuthorize(Roles.MASTER_WRITE)
    List<EquityService.NameMatch> confirmName(@Valid @RequestBody ConfirmName body) {
        return service.confirmName(body.month(), body.partyCode(), body.clientId()).nameMatches();
    }

    @GetMapping("/trades")
    @PreAuthorize(Roles.MASTER_READ)
    List<TradeView> trades(@RequestParam YearMonth month, @RequestParam String party) {
        return service.trades(month, party);
    }
}
