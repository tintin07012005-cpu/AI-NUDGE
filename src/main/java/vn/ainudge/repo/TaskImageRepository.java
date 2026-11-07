package vn.ainudge.repo;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.ainudge.model.TaskImage;

public interface TaskImageRepository extends JpaRepository<TaskImage, Long> {
    List<TaskImage> findByTask_IdOrderByIdAsc(Long taskId);
    long countByTask_Id(Long taskId);

    /** Một lần truy vấn lấy ảnh của nhiều nhiệm vụ: mỗi dòng là {mã nhiệm vụ, mã ảnh}. */
    @Query("select i.task.id, i.id from TaskImage i where i.task.id in :ids order by i.id asc")
    List<Object[]> findIdPairsByTaskIds(@Param("ids") Collection<Long> ids);
}
