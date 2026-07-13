package com.church.operation.rest;

import com.church.operation.dto.MemberRequest;
import com.church.operation.dto.MemberResponse;
import com.church.operation.dto.MemberImageContent;
import com.church.operation.entity.Member;
import com.church.operation.service.MemberService;
import com.church.operation.service.MemberImageService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/members")
public class MemberController {
    private final MemberService memberService;
    private final MemberImageService memberImageService;

    public MemberController(MemberService memberService, MemberImageService memberImageService) {
        this.memberService = memberService;
        this.memberImageService = memberImageService;
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

    @GetMapping("/me/image")
    ResponseEntity<byte[]> selfImage(Authentication authentication) {
        Member member = actor(authentication);
        return imageResponse(memberImageService.load(member, member.getId()));
    }

    @PutMapping(path = "/me/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    MemberResponse replaceSelfImage(Authentication authentication, @RequestPart("file") MultipartFile file) {
        Member member = actor(authentication);
        memberImageService.store(member, member.getId(), file);
        return MemberResponse.from(memberService.getMember(member, member.getId()));
    }

    @DeleteMapping("/me/image")
    ResponseEntity<Void> removeSelfImage(Authentication authentication) {
        Member member = actor(authentication);
        memberImageService.remove(member, member.getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    MemberResponse getMember(Authentication authentication, @PathVariable("id") String id) {
        return MemberResponse.from(memberService.getMember(actor(authentication), id));
    }

    @PutMapping("/{id}")
    MemberResponse updateMember(Authentication authentication, @PathVariable("id") String id, @Valid @RequestBody MemberRequest request) {
        return MemberResponse.from(memberService.updateMember(actor(authentication), id, request));
    }

    @GetMapping("/{id}/image")
    ResponseEntity<byte[]> image(Authentication authentication, @PathVariable("id") String id) {
        return imageResponse(memberImageService.load(actor(authentication), id));
    }

    @PutMapping(path = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    MemberResponse replaceImage(
        Authentication authentication,
        @PathVariable("id") String id,
        @RequestPart("file") MultipartFile file
    ) {
        Member member = actor(authentication);
        memberImageService.store(member, id, file);
        return MemberResponse.from(memberService.getMember(member, id));
    }

    @DeleteMapping("/{id}/image")
    ResponseEntity<Void> removeImage(Authentication authentication, @PathVariable("id") String id) {
        memberImageService.remove(actor(authentication), id);
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<byte[]> imageResponse(MemberImageContent content) {
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(content.contentType()))
            .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePrivate())
            .body(content.bytes());
    }

    private Member actor(Authentication authentication) {
        return (Member) authentication.getPrincipal();
    }
}
