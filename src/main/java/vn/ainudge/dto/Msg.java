package vn.ainudge.dto;

/** Một tin nhắn hiện trong khung chat. sender: BOT | STUDENT. kind: normal | nudge | cheer. */
public record Msg(String sender, String text, String kind) {}
