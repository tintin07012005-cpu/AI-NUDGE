package vn.ainudge.repo;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.ainudge.model.TaskProgress;

public interface TaskProgressRepository extends JpaRepository<TaskProgress, Long> {
    Optional<TaskProgress> findByTask_IdAndStudent_Id(Long taskId, Long studentId);
    List<TaskProgress> findByStudent_Id(Long studentId);
    List<TaskProgress> findByStudent_IdAndTask_IdIn(Long studentId, Collection<Long> taskIds);
    List<TaskProgress> findByTask_Id(Long taskId);
    /** Một lần truy vấn lấy tiến độ của nhiều nhiệm vụ. */
    List<TaskProgress> findByTask_IdIn(Collection<Long> taskIds);
    /** Tiến độ của một nhóm học sinh (một trang danh sách) trong một lần truy vấn. */
    List<TaskProgress> findByStudent_IdIn(Collection<Long> studentIds);
    long countByConfirmedTrue();
    long countByNeedHelpTrue();
    List<TaskProgress> findByStatusAndPlannedAtLessThanEqual(String status, LocalDateTime time);
}
