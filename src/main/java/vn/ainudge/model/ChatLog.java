package vn.ainudge.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Từng câu trong cuộc trò chuyện (của trợ lý hoặc của học sinh). */
@Entity
public class ChatLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(optional = false)
    public Conversation conversation;

    public LocalDateTime createdAt;

    /** "BOT" hoặc "STUDENT" */
    @Column(length = 10)
    public String sender;

    @Column(length = 2000)
    public String content;

    /** "normal", "nudge" (cú hích) hoặc "cheer" (khen ngợi) */
    @Column(length = 10)
    public String kind;

    /** Bước hỏi mà dòng này thuộc về */
    @Column(length = 30)
    public String step;
}
