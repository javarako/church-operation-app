package com.church.operation.service;

import com.church.operation.entity.Member;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class BootstrapAdminRunner implements ApplicationRunner {
    private final MemberService memberService;
    private final PasswordEncoder passwordEncoder;

    public BootstrapAdminRunner(MemberService memberService, PasswordEncoder passwordEncoder) {
        this.memberService = memberService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        Member admin = memberService.createBootstrapAdminMember();
        if (admin.getPasswordHash() == null || admin.getPasswordHash().isBlank()) {
            admin.setPasswordHash(passwordEncoder.encode("password"));
            admin.setMustChangePassword(true);
            memberService.save(admin);
        }
    }
}
