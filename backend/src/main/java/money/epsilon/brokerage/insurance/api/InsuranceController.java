package money.epsilon.brokerage.insurance.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import money.epsilon.brokerage.insurance.domain.InsurancePolicy.BusinessType;
import money.epsilon.brokerage.insurance.domain.InsurancePolicy.Category;
import money.epsilon.brokerage.insurance.service.InsuranceService;
import money.epsilon.brokerage.insurance.service.InsuranceService.Choices;
import money.epsilon.brokerage.insurance.service.InsuranceService.Defaults;
import money.epsilon.brokerage.insurance.service.InsuranceService.PolicyInput;
import money.epsilon.brokerage.insurance.service.InsuranceService.PolicyView;
import money.epsilon.brokerage.insurance.service.InsuranceService.UploadResult;
import money.epsilon.brokerage.platform.importing.UploadedFile;
import money.epsilon.brokerage.platform.security.Roles;
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

/** The Insurance screen (R-94, R-95): policies typed one by one or uploaded as the month's policy sheet. */
@RestController
@RequestMapping("/api/insurance")
class InsuranceController {

    private final InsuranceService service;

    InsuranceController(InsuranceService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(Roles.MASTER_READ)
    List<PolicyView> list(@RequestParam(required = false) YearMonth from, @RequestParam(required = false) YearMonth to,
                          @RequestParam(required = false) String q, @RequestParam(required = false) Long rmId,
                          @RequestParam(required = false) Long pospId, @RequestParam(required = false) String insurer,
                          @RequestParam(required = false) String platform, @RequestParam(required = false) Category category,
                          @RequestParam(defaultValue = "false") boolean includeCancelled) {
        // "platform" is the shared list filter's key: here, the insurer.
        return service.search(new InsuranceService.Filter(from, to, q, rmId, pospId, insurer != null ? insurer : platform, category, includeCancelled));
    }

    @GetMapping("/{id}")
    @PreAuthorize(Roles.MASTER_READ)
    PolicyView get(@PathVariable long id) {
        return service.get(id);
    }

    @GetMapping("/choices")
    @PreAuthorize(Roles.MASTER_READ)
    Choices choices() {
        return service.choices();
    }

    @GetMapping("/categories")
    @PreAuthorize(Roles.MASTER_READ)
    List<money.epsilon.brokerage.insurance.domain.InsuranceCategoryRules.Rule> categories() {
        return service.categories();
    }

    @GetMapping("/defaults")
    @PreAuthorize(Roles.MASTER_READ)
    Defaults defaults(@RequestParam(required = false) String insurer, @RequestParam(required = false) String productName,
                      @RequestParam(required = false) Long pospId) {
        return service.defaults(insurer, productName, pospId);
    }

    @GetMapping("/export.xlsx")
    @PreAuthorize(Roles.MASTER_READ)
    ResponseEntity<byte[]> export(@RequestParam(required = false) YearMonth from, @RequestParam(required = false) YearMonth to,
                                  @RequestParam(required = false) String q, @RequestParam(required = false) Long rmId,
                                  @RequestParam(required = false) Long pospId, @RequestParam(required = false) String insurer,
                                  @RequestParam(required = false) String platform, @RequestParam(required = false) Category category) {
        String name = from == null && to == null ? "all" : (from == null ? "" : from.toString()) + "_to_" + (to == null ? "" : to.toString());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Insurance_" + name + ".xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(service.export(new InsuranceService.Filter(from, to, q, rmId, pospId, insurer != null ? insurer : platform, category, false)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Roles.MASTER_WRITE)
    PolicyView create(@Valid @RequestBody PolicyRequest req) {
        return service.create(req.clientId(), req.newClientName(), req.newClientPan(), req.rmId(), req.input());
    }

    @PutMapping("/{id}")
    @PreAuthorize(Roles.MASTER_WRITE)
    PolicyView update(@PathVariable long id, @Valid @RequestBody PolicyRequest req) {
        return service.update(id, req.clientId(), req.rmId(), req.input());
    }

    @PostMapping("/{id}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Roles.MASTER_WRITE)
    void cancel(@PathVariable long id, @Valid @RequestBody CancelRequest req) {
        service.cancel(id, req.reason());
    }

    @PostMapping("/upload")
    @PreAuthorize(Roles.MASTER_WRITE)
    UploadResult upload(@RequestParam("file") MultipartFile file, @RequestParam("month") LocalDate month) throws IOException {
        UploadedFile f = UploadedFile.of(file, "insurance-policies.xlsx", "the insurance policy sheet");
        return service.upload(f.content(), f.name(), month);
    }

    record CancelRequest(@Size(max = 500) String reason) {
    }

    record PolicyRequest(Long clientId, @Size(max = 300) String newClientName, @Size(max = 20) String newClientPan, Long rmId,
                         @NotNull LocalDate month, Long pospId, @Size(max = 200) String newPospName, @Size(max = 200) String insurer,
                         Category category, @Size(max = 100) String subType, @Size(max = 300) String productName,
                         @Size(max = 100) String policyNo, BusinessType businessType, @Size(max = 40) String ppt, Integer policyTerm,
                         BigDecimal sumAssured, BigDecimal odPremium, BigDecimal tpPremium, BigDecimal totalPremium, BigDecimal netPremium,
                         BigDecimal expectedPercent, BigDecimal expectedComm, BigDecimal actualPercent, BigDecimal actualComm,
                         BigDecimal pospShare, BigDecimal pospComm, @Size(max = 1000) String remarks) {
        PolicyInput input() {
            return new PolicyInput(month, pospId, newPospName, insurer, category, subType, productName, policyNo, businessType, ppt, policyTerm,
                    sumAssured, odPremium, tpPremium, totalPremium, netPremium, expectedPercent, expectedComm, actualPercent, actualComm,
                    pospShare, pospComm, remarks);
        }
    }
}
