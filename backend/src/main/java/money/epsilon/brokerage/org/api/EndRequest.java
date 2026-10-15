package money.epsilon.brokerage.org.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Ends a current assignment: {@code lastDay} is the last day it still applies. */
record EndRequest(@NotNull LocalDate lastDay, @Size(max = 500) String reason) {
}
