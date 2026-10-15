package money.epsilon.brokerage.org.repository;

import java.util.Optional;
import money.epsilon.brokerage.org.domain.Employee;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    Optional<Employee> findByEmpCodeIgnoreCase(String empCode);
}
