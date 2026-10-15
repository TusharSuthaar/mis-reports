package money.epsilon.brokerage.org.service;

import java.time.LocalDate;

/**
 * Published (synchronously, inside the exit transaction) when an employee leaves, so other modules can close
 * their own dated links for that person. A listener that throws aborts the exit.
 */
public record EmployeeExited(long employeeId, String employeeName, LocalDate lastDay, String reason) {
}
