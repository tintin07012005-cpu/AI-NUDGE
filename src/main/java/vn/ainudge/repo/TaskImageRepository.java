package vn.ainudge.repo;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.ainudge.model.TaskImage;

public interface TaskImageRepository extends JpaRepository<TaskImage, Long> {
    List<TaskImage> findByTask_IdOrderByIdAsc(Long taskId);
    long countByTask_Id(Long taskId);
}
