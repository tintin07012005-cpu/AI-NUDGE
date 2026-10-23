package vn.ainudge.repo;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.ainudge.model.ProofImage;

public interface ProofImageRepository extends JpaRepository<ProofImage, Long> {
    List<ProofImage> findByProgress_IdOrderByIdAsc(Long progressId);
}
