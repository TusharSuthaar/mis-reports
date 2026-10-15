package money.epsilon.brokerage.partner.service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import money.epsilon.brokerage.partner.domain.Partner;
import money.epsilon.brokerage.partner.domain.PartnerStatus;
import money.epsilon.brokerage.partner.domain.PartnerType;
import money.epsilon.brokerage.partner.repository.PartnerRepository;
import money.epsilon.brokerage.partner.repository.PartnerRmLinkRepository;
import money.epsilon.brokerage.platform.audit.AuditService;
import money.epsilon.brokerage.platform.crypto.PanCipher;
import money.epsilon.brokerage.platform.security.Roles;
import money.epsilon.brokerage.platform.text.Text;
import money.epsilon.brokerage.platform.web.NotFoundException;
import money.epsilon.brokerage.platform.web.RuleViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Sub-brokers, referral partners and POSPs. A partner is identified by type + name. */
@Service
public class PartnerService {

    static final String AUDIT_PARTNER = "PARTNER";
    private static final Pattern GSTIN = Pattern.compile("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$");

    private final PartnerRepository partners;
    private final PartnerRmLinkRepository links;
    private final PanCipher panCipher;
    private final AuditService audit;

    PartnerService(PartnerRepository partners, PartnerRmLinkRepository links, PanCipher panCipher, AuditService audit) {
        this.partners = partners;
        this.links = links;
        this.panCipher = panCipher;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<PartnerView> list() {
        boolean fullPan = Roles.canSeeFullPan();
        return partners.findAll().stream()
                .sorted(Comparator.comparing(Partner::getType).thenComparing(p -> p.getName().toLowerCase()))
                .map(p -> view(p, fullPan))
                .toList();
    }

    @Transactional(readOnly = true)
    public PartnerView get(long id) {
        return view(find(id), Roles.canSeeFullPan());
    }

    @Transactional(readOnly = true)
    public Optional<PartnerView> findByTypeAndName(PartnerType type, String name) {
        return partners.findByTypeAndNameIgnoreCase(type, Text.required(name, "Partner name")).map(p -> view(p, Roles.canSeeFullPan()));
    }

    @Transactional
    public PartnerView create(PartnerInput in) {
        if (in.type() == null) throw new IllegalArgumentException("Partner type is required");
        String name = Text.required(in.name(), "Partner name");
        if (partners.findByTypeAndNameIgnoreCase(in.type(), name).isPresent()) {
            throw new RuleViolationException(label(in.type()) + " '" + name + "' already exists");
        }
        Partner p = new Partner(in.type(), name);
        applyDetails(p, in);
        if (in.status() != null) p.setStatus(in.status());
        partners.saveAndFlush(p);
        audit.record(AUDIT_PARTNER, p.getId(), "CREATE", null, auditView(p));
        return view(p, Roles.canSeeFullPan());
    }

    /**
     * Updates name, details and status; the type never changes. Null fields are left unchanged; for PAN an empty
     * string clears it.
     */
    @Transactional
    public PartnerView update(long id, PartnerInput in) {
        Partner p = find(id);
        if (in.type() != null && in.type() != p.getType()) throw new RuleViolationException("A partner's type cannot be changed");
        PartnerAudit before = auditView(p);
        if (in.name() != null) {
            String name = Text.required(in.name(), "Partner name");
            partners.findByTypeAndNameIgnoreCase(p.getType(), name).filter(o -> !o.getId().equals(id)).ifPresent(o -> {
                throw new RuleViolationException(label(p.getType()) + " '" + name + "' already exists");
            });
            p.setName(name);
        }
        applyDetails(p, in);
        if (in.status() != null) p.setStatus(in.status());
        partners.saveAndFlush(p);
        PartnerAudit after = auditView(p);
        if (!after.equals(before)) audit.record(AUDIT_PARTNER, id, "UPDATE", before, after);
        return view(p, Roles.canSeeFullPan());
    }

    /** True if this PAN (normalised) is the one on record. Lets callers compare without seeing the PAN. */
    @Transactional(readOnly = true)
    public boolean hasPan(long id, String pan) {
        String normalised = PanCipher.normalise(pan);
        String stored = find(id).getPanHash();
        return normalised == null ? stored == null : panCipher.hash(normalised).equals(stored);
    }

    private void applyDetails(Partner p, PartnerInput in) {
        if (in.pan() != null) {
            String pan = PanCipher.normalise(in.pan());
            if (pan == null) {
                p.setPan(null, null);
            } else {
                String hash = panCipher.hash(pan);
                partners.findByTypeAndPanHash(p.getType(), hash).stream().filter(o -> !o.getId().equals(p.getId())).findFirst()
                        .ifPresent(o -> {
                            throw new RuleViolationException("This PAN is already used by " + label(o.getType()).toLowerCase()
                                    + " '" + o.getName() + "'. Name variants are handled as aliases, not separate partners.");
                        });
                if (!hash.equals(p.getPanHash())) p.setPan(panCipher.encrypt(pan), hash);
            }
        }
        if (in.gstin() != null) {
            String gstin = Text.clean(in.gstin());
            if (gstin != null) {
                gstin = gstin.toUpperCase(Locale.ROOT);
                if (!GSTIN.matcher(gstin).matches()) throw new IllegalArgumentException("GSTIN must be 15 characters like 27ABCDE1234F1Z5");
            }
            p.setGstin(gstin);
        }
        if (in.arnOrPospCode() != null) p.setArnOrPospCode(Text.clean(in.arnOrPospCode()));
        if (in.bankRef() != null) p.setBankRef(Text.clean(in.bankRef()));
    }

    private Partner find(long id) {
        return partners.findById(id).orElseThrow(() -> new NotFoundException("Partner " + id + " not found"));
    }

    private PartnerView view(Partner p, boolean fullPan) {
        String pan = p.getPanEncrypted() == null ? null : panCipher.decrypt(p.getPanEncrypted());
        return new PartnerView(p.getId(), p.getType(), p.getName(), fullPan ? pan : PanCipher.mask(pan), p.getGstin(),
                p.getArnOrPospCode(), p.getBankRef(), p.getStatus(), links.countByPartnerIdAndValidToIsNull(p.getId()));
    }

    /** Never contains the full PAN. */
    private PartnerAudit auditView(Partner p) {
        String pan = p.getPanEncrypted() == null ? null : PanCipher.mask(panCipher.decrypt(p.getPanEncrypted()));
        return new PartnerAudit(p.getId(), p.getType(), p.getName(), pan, p.getGstin(), p.getArnOrPospCode(), p.getBankRef(), p.getStatus());
    }

    static String label(PartnerType type) {
        return switch (type) {
            case SUB_BROKER -> "Sub-broker";
            case REFERRAL -> "Referral partner";
            case POSP -> "POSP";
        };
    }

    public record PartnerInput(PartnerType type, String name, String pan, String gstin, String arnOrPospCode,
                               String bankRef, PartnerStatus status) {
    }

    /** {@code pan} is masked unless the caller is FINANCE or ADMIN. */
    public record PartnerView(Long id, PartnerType type, String name, String pan, String gstin, String arnOrPospCode,
                              String bankRef, PartnerStatus status, long currentRmLinks) {
    }

    public record PartnerAudit(Long id, PartnerType type, String name, String maskedPan, String gstin, String arnOrPospCode,
                               String bankRef, PartnerStatus status) {
    }
}
