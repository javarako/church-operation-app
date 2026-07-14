package com.church.operation.rest;

import com.church.operation.dto.MemberImageContent;
import com.church.operation.entity.Member;
import com.church.operation.exception.GlobalExceptionHandler;
import com.church.operation.exception.MemberImageNotFoundException;
import com.church.operation.service.MemberImageService;
import com.church.operation.service.MemberDeletionService;
import com.church.operation.service.MemberService;
import com.church.operation.util.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class MemberControllerTest {
    private final MemberService memberService = mock(MemberService.class);
    private final MemberImageService memberImageService = mock(MemberImageService.class);
    private final MemberDeletionService memberDeletionService = mock(MemberDeletionService.class);
    private MockMvc mockMvc;
    private Member admin;

    @BeforeEach
    void setUp() {
        mockMvc = standaloneSetup(new MemberController(memberService, memberImageService, memberDeletionService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
        admin = member("admin-id", Role.ADMIN);
    }

    @Test
    void returnsProtectedImageWithPrivateCacheHeaders() throws Exception {
        when(memberImageService.load(admin, "member-1"))
            .thenReturn(new MemberImageContent(new byte[] {1, 2, 3}, "image/png", "face.png"));

        mockMvc.perform(get("/api/members/member-1/image").principal(authentication(admin)))
            .andExpect(status().isOk())
            .andExpect(content().contentType("image/png"))
            .andExpect(content().bytes(new byte[] {1, 2, 3}))
            .andExpect(header().string("Cache-Control", "max-age=300, private"));
    }

    @Test
    void managerCanReplaceMemberImage() throws Exception {
        Member target = member("member-1", Role.MEMBER);
        when(memberService.getMember(admin, "member-1")).thenReturn(target);
        MockMultipartHttpServletRequestBuilder request = multipart("/api/members/member-1/image");
        request.with(servletRequest -> {
            servletRequest.setMethod("PUT");
            return servletRequest;
        });

        mockMvc.perform(request
                .file(new MockMultipartFile("file", "face.png", "image/png", new byte[] {1}))
                .principal(authentication(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("member-1"));

        verify(memberImageService).store(any(Member.class), any(String.class), any(MockMultipartFile.class));
    }

    @Test
    void selfServiceUsesAuthenticatedMemberId() throws Exception {
        Member self = member("member-1", Role.MEMBER);
        when(memberService.getMember(self, "member-1")).thenReturn(self);
        MockMultipartHttpServletRequestBuilder request = multipart("/api/members/me/image");
        request.with(servletRequest -> {
            servletRequest.setMethod("PUT");
            return servletRequest;
        });

        mockMvc.perform(request
                .file(new MockMultipartFile("file", "face.png", "image/png", new byte[] {1}))
                .principal(authentication(self)))
            .andExpect(status().isOk());

        verify(memberImageService).store(any(Member.class), org.mockito.ArgumentMatchers.eq("member-1"), any());
    }

    @Test
    void deleteReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/members/member-1/image").principal(authentication(admin)))
            .andExpect(status().isNoContent());

        verify(memberImageService).remove(admin, "member-1");
    }

    @Test
    void missingImageReturnsNotFound() throws Exception {
        when(memberImageService.load(admin, "member-1"))
            .thenThrow(new MemberImageNotFoundException());

        mockMvc.perform(get("/api/members/member-1/image").principal(authentication(admin)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void crossMemberAccessReturnsForbidden() throws Exception {
        when(memberImageService.load(admin, "member-1"))
            .thenThrow(new AccessDeniedException("Forbidden image."));

        mockMvc.perform(get("/api/members/member-1/image").principal(authentication(admin)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    private TestingAuthenticationToken authentication(Member principal) {
        return new TestingAuthenticationToken(principal, null);
    }

    private Member member(String id, Role role) {
        Member member = new Member();
        member.setId(id);
        member.setPrimaryEmail(id + "@example.com");
        member.setRoles(Set.of(role));
        return member;
    }
}
