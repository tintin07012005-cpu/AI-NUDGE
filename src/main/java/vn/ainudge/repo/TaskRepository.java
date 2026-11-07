package vn.ainudge.repo;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.ainudge.model.Task;

public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByActiveTrueAndDeadlineAfterOrderByDeadlineAsc(LocalDateTime after);
    List<Task> findByActiveTrueOrderByDeadlineDesc();

    /** Nhiệm vụ sắp đến hạn trong một khoảng ngắn (dùng cho bộ nhắc việc, khỏi tải mọi nhiệm vụ từ trước tới nay). */
    List<Task> findByActiveTrueAndDeadlineAfterAndDeadlineLessThanEqualOrderByDeadlineAsc(
            LocalDateTime after, LocalDateTime until);

    /**
     * Chỉ lấy các nhiệm vụ CÓ THỂ thuộc về một học sinh: giao cho tất cả, hoặc cho lớp/tổ của em.
     * (Lọc ngay ở database để không tải nhiệm vụ của cả trường; sau đó code vẫn lọc lại bằng visibleTo.)
     */
    @Query("""
            select distinct t from Task t
            left join t.classes c
            left join t.studyGroups g
            where t.active = true and t.deadline > :after
              and ((t.classes is empty and t.studyGroups is empty) or c.id = :classId or g.id in :groupIds)
            order by t.deadline asc
            """)
    List<Task> findForStudentAfter(@Param("after") LocalDateTime after, @Param("classId") Long classId,
            @Param("groupIds") Collection<Long> groupIds);

    @Query("""
            select distinct t from Task t
            left join t.classes c
            left join t.studyGroups g
            where t.active = true and t.deadline >= :from and t.deadline < :until
              and ((t.classes is empty and t.studyGroups is empty) or c.id = :classId or g.id in :groupIds)
            order by t.deadline asc
            """)
    List<Task> findForStudentBetween(@Param("from") LocalDateTime from, @Param("until") LocalDateTime until,
            @Param("classId") Long classId, @Param("groupIds") Collection<Long> groupIds);
    List<Task> findByCreatedBy_Id(Long studentId);
    List<Task> findByActiveTrueAndDeadlineGreaterThanEqualAndDeadlineLessThanOrderByDeadlineAsc(
            LocalDateTime from, LocalDateTime until);
}
