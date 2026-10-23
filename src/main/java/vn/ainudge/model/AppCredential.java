package vn.ainudge.model;
import jakarta.persistence.*;
@Entity
public class AppCredential {
    @Id @Column(length=24) public String name;
    @Column(nullable=false, length=250) public String encoded;
}
