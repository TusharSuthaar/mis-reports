package money.epsilon.brokerage.gift.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import money.epsilon.brokerage.gift.domain.GiftEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GiftEntryRepository extends JpaRepository<GiftEntry, Long> {

    List<GiftEntry> findByMonthOrderByIdAsc(LocalDate month);

    List<GiftEntry> findByMonthBetweenOrderByMonthDescIdDesc(LocalDate from, LocalDate to);

    long countByStatusAndPaymentStatusIn(GiftEntry.Status status, Collection<GiftEntry.PaymentStatus> paymentStatuses);

    /** Newest first: for suggestions (fund names). */
    List<GiftEntry> findTop500ByStatusOrderByIdDesc(GiftEntry.Status status);

    /** Every entry of these clients, all months, newest first (the client page's holdings). */
    List<GiftEntry> findByClientIdInOrderByMonthDescIdDesc(java.util.Collection<Long> clientIds);
}
