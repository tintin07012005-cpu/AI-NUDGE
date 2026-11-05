package vn.ainudge.repo;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.ainudge.model.StudyGroup;

public interface StudyGroupRepository extends JpaRepository<StudyGroup, Long> {
    List<StudyGroup> findByClassRoom_IdOrderByNameAsc(Long classId);
    Optional<StudyGroup> findByClassRoom_IdAndNameIgnoreCase(Long classId, String name);
}
