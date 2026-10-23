package vn.ainudge.dto;

import java.util.List;

/**
 * history  = tin nhắn cũ (hiện ngay, không hiệu ứng)
 * messages = tin nhắn mới của trợ lý (hiện lần lượt, có hiệu ứng gõ chữ)
 * options  = các nút trả lời nhanh
 * input    = "" (không có ô nhập) | "time" (ô chọn giờ) | "text" (ô gõ chữ)
 */
public record Reply(List<Msg> history, List<Msg> messages, List<Opt> options, String input) {}
