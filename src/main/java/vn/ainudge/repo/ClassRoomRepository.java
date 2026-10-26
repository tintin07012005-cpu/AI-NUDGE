package vn.ainudge.repo;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.ainudge.model.ClassRoom;

public interface ClassRoomRepository extends JpaRepository<ClassRoom, Long> {
    Optional<ClassRoom> findByNameIgnoreCase(String name);

    List<ClassRoom> findAllByOrderByNameAsc();
}