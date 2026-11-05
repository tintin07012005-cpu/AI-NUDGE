package vn.ainudge.model;

import jakarta.persistence.*;

/** Một tổ/nhóm cố định thuộc đúng một lớp. */
@Entity
@Table(name = "study_group", uniqueConstraints = @UniqueConstraint(
        name = "uk_group_class_name", columnNames = {"class_room_id", "name"}))
public class StudyGroup {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "class_room_id", nullable = false)
    public ClassRoom classRoom;

    @Column(nullable = false, length = 80)
    public String name;

    /** Tổ trưởng (phải là thành viên của tổ). null = chưa có. */
    @ManyToOne
    @JoinColumn(name = "leader_id")
    public Student leader;
}
