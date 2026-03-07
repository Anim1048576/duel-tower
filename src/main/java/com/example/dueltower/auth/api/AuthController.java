package com.example.dueltower.auth.api;

import com.example.dueltower.auth.dto.AuthUserResponse;
import com.example.dueltower.auth.dto.LoginRequest;
import com.example.dueltower.auth.dto.SignupRequest;
import com.example.dueltower.member.Member;
import com.example.dueltower.member.MemberRepository;
import com.example.dueltower.member.RoleType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/signup")
    public AuthUserResponse signup(@RequestBody(required = false) SignupRequest req) {
        if (req == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request body is required");
        }
        String username = normalize(req.username());
        String password = req.password();

        if (username.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "username is required");
        }
        if (password == null || password.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "password is required");
        }
        if (memberRepository.existsByUsernameAndDeletedFalse(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 사용 중인 username 입니다.");
        }
        Member member = Member.builder()
                .username(username)
                .email(buildSignupEmail(username, req.email()))
                .password(passwordEncoder.encode(password))
                .role(RoleType.USER)
                .deleted(false)
                .build();
        Member saved = memberRepository.save(member);

        return new AuthUserResponse(saved.getUsername(), List.of("ROLE_" + saved.getRole().name()));
    }

    @PostMapping("/login")
    public AuthUserResponse login(@RequestBody(required = false) LoginRequest req, HttpServletRequest request) {
        if (req == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request body is required");
        }
        String username = normalize(req.username());
        String password = req.password();

        if (username.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "username is required");
        }
        if (password == null || password.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "password is required");
        }

        try {
            Authentication auth = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(username, password)
            );

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(auth);
            SecurityContextHolder.setContext(context);

            HttpSession session = request.getSession(true);
            session.setAttribute("SPRING_SECURITY_CONTEXT", context);

            return new AuthUserResponse(
                    auth.getName(),
                    auth.getAuthorities().stream().map(a -> a.getAuthority()).toList()
            );
        } catch (BadCredentialsException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다.");
        }
    }

    @GetMapping("/me")
    public AuthUserResponse me(Authentication authentication) {
        return new AuthUserResponse(
                authentication.getName(),
                authentication.getAuthorities().stream().map(a -> a.getAuthority()).toList()
        );
    }

    @PostMapping("/logout")
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        response.setStatus(HttpStatus.NO_CONTENT.value());
    }


    private String buildSignupEmail(String username, String requestedEmail) {
        String normalizedEmail = normalize(requestedEmail);
        if (!normalizedEmail.isBlank()) {
            return normalizedEmail;
        }
        return username + "@signup.local";
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
