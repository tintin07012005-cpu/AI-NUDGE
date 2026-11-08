package vn.ainudge.repo;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.ainudge.model.Conversation;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    Optional<Conversation> findFirstByStudent_IdOrderByStartedAtDesc(Long studentId);
    List<Conversation> findByStudent_IdOrderByStartedAtDesc(Long studentId);

    /** Số học sinh đã vào trò chuyện kể từ một thời điểm (dùng cho thẻ "Học sinh vào hôm nay"). */
    @Query("select count(distinct c.student.id) from Conversation c where c.startedAt >= :from")
    long countStudentsStartedSince(@Param("from") LocalDateTime from);

    /** Mỗi dòng: {mã học sinh, số lần trò chuyện, lần hoạt động gần nhất}. */
    @Query("select c.student.id, count(c), max(c.lastActivity) from Conversation c where c.student.id in :ids group by c.student.id")
    List<Object[]> summarizeByStudents(@Param("ids") Collection<Long> ids);
}
