package vn.ainudge.dto;

import java.util.List;

public record ConvView(String time, List<QA> qa, List<Msg> chat) {}
