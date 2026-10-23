package vn.ainudge.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import vn.ainudge.service.ProofService;

/** Báo lỗi gọn khi gửi ảnh quá lớn (thay vì trang lỗi trắng). */
@RestControllerAdvice
public class UploadErrors {
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Object> tooBig(HttpServletRequest req) {
        // Ban cán sự gửi ảnh đề bằng form thường: quay lại trang ban cán sự kèm lời báo
        if (req.getRequestURI().startsWith("/monitor"))
            return ResponseEntity.status(302).header("Location", "/monitor?tooBig=1").build();
        return ResponseEntity.status(413).body(new ProofService.Result(0, 0,
            "Ảnh quá lớn. Mỗi ảnh tối đa 10 MB, mỗi lần gửi tối đa 60 MB."));
    }
}
