package vn.ainudge.repo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.ainudge.model.TaskProgress;

public interface TaskProgressRepository extends JpaRepository<TaskProgress, Long> {
    Optional<TaskProgress> findByTask_IdAndStudent_Id(Long taskId, Long studentId);
    List<TaskProgress> findByStudent_Id(Long studentId);
    List<TaskProgress> findByTask_Id(Long taskId);
    List<TaskProgress> findByStatusAndPlannedAtLessThanEqual(String status, LocalDateTime time);
}
