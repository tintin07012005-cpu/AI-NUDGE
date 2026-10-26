package vn.ainudge.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import vn.ainudge.model.ClassRoom;
import vn.ainudge.model.Student;
import vn.ainudge.repo.ClassRoomRepository;
import vn.ainudge.repo.StudentRepository;
import vn.ainudge.service.NotifyService;
import vn.ainudge.service.RememberService;

@Controller
public class AuthController {
    private final StudentRepository students;
    private final RememberService remember;
    private final NotifyService notify;

    private final vn.ainudge.service.AppCredentialService credentials;
    private final ClassRoomRepository classRooms;

    public AuthController(StudentRepository students, RememberService remember, NotifyService notify,
            vn.ainudge.service.AppCredentialService credentials,
            ClassRoomRepository classRooms) {
        this.classRooms = classRooms;
        this.credentials = credentials;
        this.students = students;
        this.notify = notify;
        this.remember = remember;
    }

    /**
     * Danh sách lớp cho ô chọn lớp ở form đăng ký (tự thêm vào mọi trang của
     * controller này).
     */
    @ModelAttribute("classes")
    public List<ClassRoom> classList() {
        return classRooms.findAllByOrderByNameAsc();
    }

    /**
     * Đã đăng nhập từ trước (còn thẻ ghi nhớ) thì vào thẳng trang trò chuyện, không
     * hiện màn hình đăng nhập.
     */
    @GetMapping("/")
    public String home(HttpServletRequest req, HttpServletResponse res) {
        remember.restore(req, res);
        HttpSession s = req.getSession(false);
        Object sid = s == null ? null : s.getAttribute("studentId");
        if (sid != null && students.existsById((Long) sid))
            return "redirect:/student";
        return "login";
    }

    /** Đăng nhập bằng email (hoặc tên trong danh sách lớp) + mật khẩu. */
    @PostMapping("/login")
    public String login(@RequestParam("name") String id, @RequestParam(defaultValue = "") String password,
            HttpServletRequest req, HttpServletResponse res, HttpSession session, Model model) {
        String key = id == null ? "" : id.trim().replaceAll("\\s+", " ");
        if (key.isEmpty()) {
            model.addAttribute("error", "Bạn hãy nhập email của mình nhé.");
            return "login";
        }
        Student s = key.contains("@")
                ? students.findFirstByEmailIgnoreCase(key).orElse(null)
                : students.findByNameIgnoreCase(key).orElse(null);
        String pw = password.trim();
        if (s == null || s.password == null || pw.isEmpty() || !s.password.equalsIgnoreCase(pw)) {
            model.addAttribute("error",
                    "Email hoặc mật khẩu chưa đúng. Nếu chưa có tài khoản, bạn bấm \"Đăng ký\" nhé.");
            return "login";
        }
        session.setAttribute("studentId", s.id);
        remember.rememberStudent(req, res, s);
        return "redirect:/student";
    }

    /**
     * Học sinh tự đăng ký bằng tên + email + mật khẩu, xong vào thẳng trang trò
     * chuyện.
     */
    @PostMapping("/register")
    public String register(@RequestParam String name, @RequestParam String email,
            @RequestParam String password, @RequestParam(defaultValue = "") String password2,
            @RequestParam(required = false) Long classId,
            HttpServletRequest req, HttpServletResponse res, HttpSession session, Model model) {
        String n = name == null ? "" : name.trim().replaceAll("\\s+", " ");
        String em = email == null ? "" : email.trim().toLowerCase();
        String pw = password == null ? "" : password.trim();
        model.addAttribute("mode", "register");
        model.addAttribute("regName", n);
        model.addAttribute("regEmail", em);
        model.addAttribute("regClass", classId);
        if (n.isEmpty() || n.length() > 60) {
            model.addAttribute("error", "Bạn hãy nhập tên (tối đa 60 ký tự) nhé.");
            return "login";
        }
        if (!em.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$") || em.length() > 150) {
            model.addAttribute("error", "Email chưa đúng định dạng.");
            return "login";
        }
        if (!pw.matches("\\S{4,20}")) {
            model.addAttribute("error", "Mật khẩu dài 4–20 ký tự, không có dấu cách.");
            return "login";
        }
        if (!pw.equals(password2.trim())) {
            model.addAttribute("error", "Hai ô mật khẩu chưa giống nhau.");
            return "login";
        }
        if (students.findFirstByEmailIgnoreCase(em).isPresent()) {
            model.addAttribute("error", "Email này đã đăng ký rồi. Bạn bấm \"Đăng nhập\" nhé.");
            return "login";
        }
        if (students.findByNameIgnoreCase(n).isPresent()) {
            model.addAttribute("error",
                    "Tên này đã có người dùng. Bạn thêm lớp hoặc chữ cái phân biệt (vd: \"" + n + " 8A\") nhé.");
            return "login";
        }
        ClassRoom cls = classId == null ? null : classRooms.findById(classId).orElse(null);
        if (cls == null && classRooms.count() > 0) { // đã có lớp thì học sinh phải chọn lớp (hoặc ghi danh sau)
            model.addAttribute("error", "Bạn chọn lớp của mình nhé.");
            return "login";
        }
        Student s = new Student();
        s.name = n;
        s.email = em;
        s.classRoom = cls;
        s.password = pw;
        s.createdAt = LocalDateTime.now();
        s = students.save(s);
        notify.welcomeAsync(s);
        session.setAttribute("studentId", s.id);
        remember.rememberStudent(req, res, s);
        return "redirect:/student";
    }

    @PostMapping("/logout")
    public String logout(HttpSession session, HttpServletResponse res) {
        session.invalidate();
        remember.forgetAll(res);
        return "redirect:/";
    }

    @GetMapping("/teacher/login")
    public String teacherLoginPage(HttpServletRequest req, HttpServletResponse res) {
        remember.restore(req, res);
        HttpSession s = req.getSession(false);
        if (s != null && s.getAttribute("teacher") != null)
            return "redirect:/teacher";
        return "teacher-login";
    }

    @PostMapping("/teacher/login")
    public String teacherLogin(@RequestParam String password, HttpServletRequest req, HttpServletResponse res,
            HttpSession session, Model model) {
        if (!credentials.verify("admin", password)) {
            model.addAttribute("error", "Sai mật khẩu.");
            return "teacher-login";
        }
        session.setAttribute("teacher", true);
        remember.rememberAdmin(req, res);
        return "redirect:/teacher";
    }
}