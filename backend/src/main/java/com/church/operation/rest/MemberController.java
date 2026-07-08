package com.church.operation.rest;

import com.church.operation.dto.MemberRequest;
import com.church.operation.dto.MemberResponse;
import com.church.operation.entity.Member;
import com.church.operation.service.MemberService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/members")
public class MemberController {
    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping
    List<MemberResponse> listMembers(Authentication authentication, @RequestParam(name = "search", required = false) String search) {
        return memberService.listMembers(actor(authentication), search).stream()
            .map(MemberResponse::from)
            .toList();
    }

    @PostMapping
    MemberResponse createMember(Authentication authentication, @Valid @RequestBody MemberRequest request) {
        return MemberResponse.from(memberService.createMember(actor(authentication), request));
    }

    @GetMapping("/me")
    MemberResponse getSelf(Authentication authentication) {
        return MemberResponse.from(memberService.getSelf(actor(authentication)));
    }

    @PutMapping("/me")
    MemberResponse updateSelf(Authentication authentication, @RequestBody MemberRequest request) {
        return MemberResponse.from(memberService.updateSelf(actor(authentication), request));
    }

    @GetMapping("/{id}")
    MemberResponse getMember(Authentication authentication, @PathVariable("id") String id) {
        return MemberResponse.from(memberService.getMember(actor(authentication), id));
    }

    @PutMapping("/{id}")
    MemberResponse updateMember(Authentication authentication, @PathVariable("id") String id, @Valid @RequestBody MemberRequest request) {
        return MemberResponse.from(memberService.updateMember(actor(authentication), id, request));
    }

    private Member actor(Authentication authentication) {
        return (Member) authentication.getPrincipal();
    }
}
