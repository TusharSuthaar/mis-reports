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

    /**
     * A product entry (FD, bond, AIF, loan, GIFT, Unlisted, PMS fee, NPS, P2P, insurance, equity) of the month holding
     * {@code day} is added, changed or cancelled (R-98). Refused in a LOCKED month; a PROCESSED month goes back to OPEN so
     * its split and END are made again. Takes the month's row lock, so it waits for a running process or lock.
     * Call inside the change's transaction, before making it, for every month the change touches (old and new).
     * @throws money.epsilon.brokerage.platform.web.RuleViolationException if the month is locked
     */
    void beforeEntryChange(LocalDate day);
}
