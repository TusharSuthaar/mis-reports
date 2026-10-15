package money.epsilon.brokerage.platform.dated;

import java.time.LocalDate;

/**
 * Guards months: a dated change (RM, branch, region, RD, partner share) may not take effect on a day inside a LOCKED
 * month, because that month's figures are final. A PROCESSED month the change reaches goes back to OPEN, since its
 * split no longer matches. Implemented by the period module.
 */
public interface ChangeWindow {

    /**
     * Call inside the change's transaction, before making it.
     * @throws money.epsilon.brokerage.platform.web.RuleViolationException if {@code firstAffectedDay} is in a locked month
     */
    void beforeChange(LocalDate firstAffectedDay);
}
