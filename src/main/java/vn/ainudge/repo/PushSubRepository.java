package vn.ainudge.repo;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.ainudge.model.PushSub;

public interface PushSubRepository extends JpaRepository<PushSub, Long> {
    List<PushSub> findByStudent_Id(Long studentId);
    Optional<PushSub> findFirstByEndpoint(String endpoint);
}
