package vn.ainudge.repo;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.ainudge.model.Task;

public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByActiveTrueAndDeadlineAfterOrderByDeadlineAsc(LocalDateTime after);
    List<Task> findByActiveTrueOrderByDeadlineDesc();
    List<Task> findByCreatedBy_Id(Long studentId);
}
