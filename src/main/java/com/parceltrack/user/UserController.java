package com.parceltrack.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@Validated
public class UserController {

    private final UserService userService;
    private final UserRepository userRepository;

    public UserController(UserService userService, UserRepository userRepository) {
        this.userService = userService;
        this.userRepository = userRepository;
    }

    @GetMapping("/login")
    public String login() {
        return "auth/login";
    }

    @GetMapping({"/register", "/seller/register"})
    public String registerForm(Model model) {
        if (!model.containsAttribute("registration")) {
            model.addAttribute("registration", new RegistrationRequest());
        }
        return "auth/register";
    }

    @PostMapping({"/register", "/seller/register"})
    public String register(@Validated @ModelAttribute("registration") RegistrationRequest request,
                           BindingResult bindingResult, Model model) {
        if (!request.password.equals(request.confirmPassword)) {
            bindingResult.rejectValue("confirmPassword", "password.mismatch",
                    "Password and confirmation do not match.");
        }
        if (bindingResult.hasErrors()) {
            return "auth/register";
        }
        try {
            userService.registerSeller(request.fullName, request.email, request.password);
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
            return "auth/register";
        }
        return "redirect:/login?registered";
    }

    @GetMapping("/dashboard")
    public String dashboard(Authentication authentication) {
        if (authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"))) {
            return "redirect:/admin/dashboard";
        }
        if (authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_RIDER"))) {
            return "redirect:/rider/parcels";
        }
        return "redirect:/seller/parcels";
    }

    @GetMapping("/seller/parcels")
    public String sellerPage() {
        return "seller/parcels";
    }

    // GET /rider/parcels and GET /admin/dashboard are served by M3
    // (com.parceltrack.delivery.DeliveryController / AdminDashboardController).

    @PostMapping("/admin/riders")
    public String createRider(@Validated @ModelAttribute("rider") RiderRequest request,
                              BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("riders", userRepository.findAll().stream()
                    .filter(user -> user.getRole() == Role.RIDER)
                    .toList());
            return "admin/dashboard";
        }
        try {
            userService.createRider(request.fullName, request.email, request.password);
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("riders", userRepository.findAll().stream()
                    .filter(user -> user.getRole() == Role.RIDER)
                    .toList());
            return "admin/dashboard";
        }
        return "redirect:/admin/dashboard?created";
    }

    @GetMapping("/change-password")
    public String changePasswordForm() {
        return "auth/change-password";
    }

    @PostMapping("/change-password")
    public String changePassword(Authentication authentication,
                                 @RequestParam("currentPassword") String currentPassword,
                                 @RequestParam("newPassword") String newPassword,
                                 @RequestParam("confirmPassword") String confirmPassword,
                                 Model model) {
        if (!newPassword.equals(confirmPassword)) {
            model.addAttribute("error", "New password and confirmation do not match.");
            return "auth/change-password";
        }
        try {
            userService.changePassword(authentication.getName(), currentPassword, newPassword);
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
            return "auth/change-password";
        }
        return "redirect:/dashboard?passwordChanged";
    }

    public static class RegistrationRequest {
        @NotBlank
        @Size(max = 100)
        private String fullName = "";
        @NotBlank
        @Email
        private String email = "";
        @Size(min = 10)
        private String password = "";
        private String confirmPassword = "";

        public String getFullName() { return fullName; }
        public void setFullName(String fullName) { this.fullName = fullName; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getConfirmPassword() { return confirmPassword; }
        public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
    }

    public static class RiderRequest {
        @NotBlank
        @Size(max = 100)
        private String fullName = "";
        @NotBlank
        @Email
        private String email = "";
        @Size(min = 10)
        private String password = "";

        public String getFullName() { return fullName; }
        public void setFullName(String fullName) { this.fullName = fullName; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
}
