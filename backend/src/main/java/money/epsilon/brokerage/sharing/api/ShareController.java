package money.epsilon.brokerage.sharing.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import money.epsilon.brokerage.partner.domain.PartnerType;
import money.epsilon.brokerage.platform.security.Roles;
import money.epsilon.brokerage.sharing.service.PartnerMergeService;
import money.epsilon.brokerage.sharing.service.PartnerMergeService.MergeResult;
import money.epsilon.brokerage.sharing.service.ShareService;
import money.epsilon.brokerage.sharing.service.ShareService.BranchPartnerView;
import money.epsilon.brokerage.sharing.service.ShareService.ProductShareView;
import money.epsilon.brokerage.sharing.service.ShareService.ShareView;
import money.epsilon.brokerage.sharing.domain.Product;
import money.epsilon.brokerage.platform.web.RuleViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ShareController {

    private static final int MAX_IDS = 500;

    private final ShareService service;
    private final PartnerMergeService merges;

    ShareController(ShareService service, PartnerMergeService merges) {
        this.service = service;
        this.merges = merges;
    }

    /** Merge a duplicate partner (another spelling of the same person) into the right one. */
    @PostMapping("/api/partners/{id}/merge")
    @PreAuthorize(Roles.MASTER_WRITE)
    MergeResult merge(@PathVariable long id, @Valid @RequestBody MergeRequest req) {
        return merges.merge(id, req.intoId(), req.reason());
    }

    /** Current partner shares (sub-broker first) for each of the given clients (missing = no partner). */
    @GetMapping("/api/shares")
    @PreAuthorize(Roles.MASTER_READ)
    Map<Long, List<ShareView>> current(@RequestParam List<Long> clientIds) {
        if (clientIds.size() > MAX_IDS) throw new IllegalArgumentException("At most " + MAX_IDS + " clients at a time");
        return service.current(clientIds);
    }

    @GetMapping("/api/shares/partner-counts")
    @PreAuthorize(Roles.MASTER_READ)
    Map<Long, Long> partnerCounts() {
        return service.clientCountsByPartner();
    }

    /** Every partner's current % per product (0-100). */
    @GetMapping("/api/partners/product-percents")
    @PreAuthorize(Roles.MASTER_READ)
    Map<Long, Map<Product, BigDecimal>> productPercents() {
        return service.currentProductPercents();
    }

    @GetMapping("/api/partners/{id}/products")
    @PreAuthorize(Roles.MASTER_READ)
    List<ProductShareView> productHistory(@PathVariable long id) {
        return service.productHistory(id);
    }

    @PostMapping("/api/partners/{id}/products")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Roles.MASTER_WRITE)
    void setProductPercent(@PathVariable long id, @Valid @RequestBody SetProductPercentRequest req) {
        if (!service.setProductPercent(id, req.product(), req.percent(), req.from(), req.reason())) {
            throw new RuleViolationException("That is already the partner's % for " + req.product().heading());
        }
    }

    /** Branch rules in force today (a partner for every client of the branch). */
    @GetMapping("/api/branch-partners")
    @PreAuthorize(Roles.MASTER_READ)
    List<BranchPartnerView> branchPartners() {
        return service.currentBranchPartners();
    }

    @GetMapping("/api/branches/{id}/partner")
    @PreAuthorize(Roles.MASTER_READ)
    List<BranchPartnerView> branchPartnerHistory(@PathVariable long id) {
        return service.branchPartnerHistory(id);
    }

    @PostMapping("/api/branches/{id}/partner")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Roles.MASTER_WRITE)
    void setBranchPartner(@PathVariable long id, @Valid @RequestBody BranchPartnerRequest req) {
        service.setBranchPartner(id, req.partnerId(), req.from(), req.reason());
    }

    @PostMapping("/api/branches/{id}/partner/end")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Roles.MASTER_WRITE)
    void endBranchPartner(@PathVariable long id, @Valid @RequestBody EndBranchPartnerRequest req) {
        service.endBranchPartner(id, req.lastDay(), req.reason());
    }

    record BranchPartnerRequest(@NotNull Long partnerId, @NotNull LocalDate from, @Size(max = 500) String reason) {
    }

    record EndBranchPartnerRequest(@NotNull LocalDate lastDay, @Size(max = 500) String reason) {
    }

    @GetMapping("/api/clients/{id}/partner")
    @PreAuthorize(Roles.MASTER_READ)
    List<ShareView> history(@PathVariable long id) {
        return service.history(id);
    }

    @PostMapping("/api/clients/{id}/partner")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Roles.MASTER_WRITE)
    void set(@PathVariable long id, @Valid @RequestBody SetShareRequest req) {
        service.set(id, req.partnerId(), req.percent(), req.from(), req.reason());
    }

    @PostMapping("/api/clients/{id}/partner/end")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Roles.MASTER_WRITE)
    void end(@PathVariable long id, @Valid @RequestBody EndShareRequest req) {
        service.end(id, req.role(), req.lastDay(), req.reason());
    }

    /** {@code percent} null = the partner's own % per product applies (no exception for this client). */
    record SetShareRequest(@NotNull Long partnerId, BigDecimal percent, @NotNull LocalDate from, @Size(max = 500) String reason) {
    }

    record SetProductPercentRequest(@NotNull Product product, @NotNull BigDecimal percent, @NotNull LocalDate from,
                                    @Size(max = 500) String reason) {
    }

    record MergeRequest(@NotNull Long intoId, @Size(max = 500) String reason) {
    }

    record EndShareRequest(@NotNull PartnerType role, @NotNull LocalDate lastDay, @Size(max = 500) String reason) {
    }
}
