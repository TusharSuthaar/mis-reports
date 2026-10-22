package money.epsilon.brokerage.equity.api;

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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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
    @GetMapping
    @PreAuthorize(Roles.MASTER_READ)
    List<ClientMonth> list(@RequestParam(required = false) YearMonth from, @RequestParam(required = false) YearMonth to,
                           @RequestParam(required = false) String q, @RequestParam(required = false) Long rmId) {
        return service.clientMonths(new EquityService.Filter(from, to, q, rmId));
    }

    @GetMapping("/trades")
    @PreAuthorize(Roles.MASTER_READ)
    List<TradeView> trades(@RequestParam YearMonth month, @RequestParam String party) {
        return service.trades(month, party);
    }
}
