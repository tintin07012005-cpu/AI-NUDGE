package vn.ainudge.repo;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.ainudge.model.Student;

public interface StudentRepository extends JpaRepository<Student, Long> {
    Optional<Student> findByNameIgnoreCase(String name);
    Optional<Student> findFirstByEmailIgnoreCase(String email);
}
