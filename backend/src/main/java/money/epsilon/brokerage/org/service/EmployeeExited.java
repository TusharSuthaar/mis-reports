package money.epsilon.brokerage.org.service;

import java.time.LocalDate;

/**
 * Published (synchronously, inside the exit transaction) when an employee leaves, so other modules can close
 * their own dated links for that person. A listener that throws aborts the exit. {@code replacedBy}: the person who
 * takes over their clients (and, if an RM, their partners) from the day after {@code lastDay}; null = nobody yet (R-61).
 */
public record EmployeeExited(long employeeId, String employeeName, LocalDate lastDay, String reason, Long replacedBy) {
}
