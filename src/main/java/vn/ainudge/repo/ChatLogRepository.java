package vn.ainudge.repo;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.ainudge.model.ChatLog;

public interface ChatLogRepository extends JpaRepository<ChatLog, Long> {
    List<ChatLog> findByConversation_IdOrderByIdAsc(Long conversationId);
    List<ChatLog> findBySenderOrderByIdAsc(String sender);
    List<ChatLog> findByConversation_Student_Id(Long studentId);
}
