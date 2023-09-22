package com.example.backend.user;

import com.example.backend.session.Session;
import com.example.backend.session.SessionService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.example.backend.user.UserService.sessions;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class UserController {
    private final UserService userService;

     private final SessionService sessionService;

    public UserController(UserService userService, SessionService sessionService) {
        this.userService = userService;
        this.sessionService = sessionService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> logUserIn(@RequestBody Map<String, String> requestBody, @CookieValue(name = "session_token", required = false) String sessionTokenCookie,
                                       HttpServletResponse response) throws IOException {
        User user = userService.findByEmail(requestBody.get("email"));
        if (user == null) {
            // User does not exist
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");
        }

        // Compare the provided password with the user's hashed password
        if (!userService.isPasswordMatch(requestBody.get("password"), user.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid password");
        }

        String sessionToken;
        if (!StringUtils.hasText(sessionTokenCookie)) {
            // Generate a new session UUID
            sessionToken = UUID.randomUUID().toString();
            Cookie sessionCookie = createCookie(user, sessionToken);
            System.out.println("Created session cookie for user: " + user.getEmail());
            sessionService.saveSession(user, sessionToken); // Save the session with the generated UUID
            response.addCookie(sessionCookie); // Add the session cookie to the response
        } else {
            sessionToken = sessionTokenCookie;
            sessionService.saveSession(user, sessionToken); // Save the session with the existing UUID
        }

        // Create a JSON response that includes the session token
        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("userId", user.getId());
        responseBody.put("sessionToken", sessionToken);
        return ResponseEntity.ok(responseBody);
    }
    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody User user) {
        // Check if the email is already taken
        if (userService.isEmailTaken(user.getEmail())) {
            return ResponseEntity.badRequest().body("Email is already taken");
        }

        User savedUser = userService.saveUser(user);

        return ResponseEntity.ok(savedUser);
    }

    private Cookie createCookie(User user, String sessionToken) throws IOException {


        Session session = new Session();
        session.setUser(user);
        session.setLastSeen(LocalDateTime.now());

        sessions.put(sessionToken.toString(), session);

        Cookie sessionCookie = new Cookie("session_token", sessionToken.toString());
        sessionCookie.setSecure(true);
        sessionCookie.setPath("/");
        sessionCookie.setMaxAge(2 * 60 * 60); // 2 hours in seconds
        return sessionCookie;

    }

    private String getSessionInfo(Map<String, Session> sessions) {
        StringBuilder info = new StringBuilder("{");
        for (Map.Entry<String, Session> entry : sessions.entrySet()) {
            Session session = entry.getValue();
            User user = session.getUser();
            info.append(entry.getKey()).append(": ")
                    .append("email=").append(user.getEmail())
                    .append(", lastSeen=").append(session.getLastSeen())
                    .append(" | ");
        }
        if (!sessions.isEmpty()) {
            info.setLength(info.length() - 3); // Remove the last " | "
        }
        info.append("}");
        return info.toString();
    }
}