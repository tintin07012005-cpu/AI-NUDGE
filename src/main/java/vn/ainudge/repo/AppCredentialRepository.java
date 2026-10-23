package vn.ainudge.repo;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.ainudge.model.AppCredential;
public interface AppCredentialRepository extends JpaRepository<AppCredential, String> { }
