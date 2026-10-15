package money.epsilon.brokerage.partner.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import money.epsilon.brokerage.partner.domain.PartnerStatus;
import money.epsilon.brokerage.partner.domain.PartnerType;
import money.epsilon.brokerage.partner.service.PartnerRmLinkService;
import money.epsilon.brokerage.partner.service.PartnerRmLinkService.LinkView;
import money.epsilon.brokerage.partner.service.PartnerService;
import money.epsilon.brokerage.partner.service.PartnerService.PartnerInput;
import money.epsilon.brokerage.partner.service.PartnerService.PartnerView;
import money.epsilon.brokerage.platform.security.Roles;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/partners")
class PartnerController {

    private final PartnerService partners;
    private final PartnerRmLinkService links;

    PartnerController(PartnerService partners, PartnerRmLinkService links) {
        this.partners = partners;
        this.links = links;
    }

    @GetMapping
    @PreAuthorize(Roles.MASTER_READ)
    List<PartnerView> list() {
        return partners.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize(Roles.MASTER_READ)
    PartnerView get(@PathVariable long id) {
        return partners.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Roles.MASTER_WRITE)
    PartnerView create(@Valid @RequestBody CreatePartnerRequest req) {
        return partners.create(new PartnerInput(req.type(), req.name(), req.pan(), req.gstin(), req.arnOrPospCode(),
                req.bankRef(), req.status()));
    }

    @PatchMapping("/{id}")
    @PreAuthorize(Roles.MASTER_WRITE)
    PartnerView update(@PathVariable long id, @Valid @RequestBody UpdatePartnerRequest req) {
        return partners.update(id, new PartnerInput(null, req.name(), req.pan(), req.gstin(), req.arnOrPospCode(),
                req.bankRef(), req.status()));
    }

    @GetMapping("/{id}/rm-links")
    @PreAuthorize(Roles.MASTER_READ)
    List<LinkView> links(@PathVariable long id) {
        return links.links(id);
    }

    @PostMapping("/{id}/rm-links")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Roles.MASTER_WRITE)
    LinkView link(@PathVariable long id, @Valid @RequestBody LinkRequest req) {
        return links.link(id, req.rmId(), req.from(), req.to(), req.reason());
    }

    @PostMapping("/{id}/rm-links/{linkId}/end")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Roles.MASTER_WRITE)
    void endLink(@PathVariable long id, @PathVariable long linkId, @Valid @RequestBody EndLinkRequest req) {
        links.endLink(id, linkId, req.lastDay(), req.reason());
    }

    record CreatePartnerRequest(@NotNull PartnerType type, @NotBlank @Size(max = 200) String name, String pan,
                                @Size(max = 15) String gstin, @Size(max = 50) String arnOrPospCode,
                                @Size(max = 200) String bankRef, PartnerStatus status) {
    }

    record UpdatePartnerRequest(@Size(min = 1, max = 200) String name, String pan, @Size(max = 15) String gstin,
                                @Size(max = 50) String arnOrPospCode, @Size(max = 200) String bankRef, PartnerStatus status) {
    }

    record LinkRequest(@NotNull Long rmId, @NotNull LocalDate from, LocalDate to, @Size(max = 500) String reason) {
    }

    record EndLinkRequest(@NotNull LocalDate lastDay, @Size(max = 500) String reason) {
    }
}
