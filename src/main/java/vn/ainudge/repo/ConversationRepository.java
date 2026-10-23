package vn.ainudge.repo;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.ainudge.model.Conversation;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    Optional<Conversation> findFirstByStudent_IdOrderByStartedAtDesc(Long studentId);
    List<Conversation> findByStudent_IdOrderByStartedAtDesc(Long studentId);
}
