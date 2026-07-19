package com.church.operation.service;

import com.church.operation.config.MemberImageProperties;
import com.church.operation.entity.Member;
import com.church.operation.repo.MemberRepository;
import com.church.operation.util.Role;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.util.unit.DataSize;
import org.springframework.mock.web.MockMultipartFile;

import java.io.InputStream;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberImageServiceTest {
    private static final String MEMBER_ID = "member-1";

    @Mock private GridFsTemplate gridFsTemplate;
    @Mock private MemberRepository memberRepository;

    private MemberImageService service;
    private Member target;

    @BeforeEach
    void setUp() {
        service = new MemberImageService(
            gridFsTemplate,
            memberRepository,
            new MemberImageProperties(DataSize.ofMegabytes(5))
        );
        target = member(MEMBER_ID, Role.MEMBER);
    }

    @Test
    void storesValidPngAndReturnsGridFsId() {
        ObjectId imageId = new ObjectId("64b000000000000000000001");
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(target));
        when(gridFsTemplate.store(
            any(InputStream.class), eq("face.png"), eq("image/png"), any(Document.class)
        )).thenReturn(imageId);
        when(memberRepository.save(target)).thenReturn(target);

        String storedId = service.store(admin(), MEMBER_ID, png());

        assertThat(storedId).isEqualTo(imageId.toHexString());
        assertThat(target.getFaceImageAttachmentId()).isEqualTo(imageId.toHexString());
    }

    @Test
    void rejectsFileLargerThanFiveMegabytes() {
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(target));
        MockMultipartFile file = new MockMultipartFile(
            "file", "face.png", "image/png", new byte[5 * 1024 * 1024 + 1]
        );

        assertThatThrownBy(() -> service.store(admin(), MEMBER_ID, file))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Member image must not exceed 5 MB.");

        verifyNoInteractions(gridFsTemplate);
    }

    @Test
    void rejectsDeclaredJpegWithInvalidSignature() {
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(target));
        MockMultipartFile file = new MockMultipartFile(
            "file", "face.jpg", "image/jpeg", pngBytes()
        );

        assertThatThrownBy(() -> service.store(admin(), MEMBER_ID, file))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Image content does not match image/jpeg.");
    }

    @Test
    void memberCannotReplaceAnotherMembersImage() {
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service.store(member("other-member", Role.MEMBER), MEMBER_ID, png()))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessage("You do not have permission to manage this member image.");
    }

    @Test
    void replacementDeletesOldFileOnlyAfterMemberSave() {
        String oldId = "64b000000000000000000000";
        ObjectId newId = new ObjectId("64b000000000000000000001");
        target.setFaceImageAttachmentId(oldId);
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(target));
        when(gridFsTemplate.store(
            any(InputStream.class), eq("face.png"), eq("image/png"), any(Document.class)
        )).thenReturn(newId);
        when(memberRepository.save(target)).thenReturn(target);

        service.store(admin(), MEMBER_ID, png());

        InOrder order = inOrder(gridFsTemplate, memberRepository);
        order.verify(gridFsTemplate).store(
            any(InputStream.class), eq("face.png"), eq("image/png"), any(Document.class)
        );
        order.verify(memberRepository).save(target);
        order.verify(gridFsTemplate).delete(any());
    }

    private Member admin() {
        return member("admin-id", Role.ADMIN);
    }

    private Member member(String id, Role role) {
        Member member = new Member();
        member.setId(id);
        member.setRoles(Set.of(role));
        return member;
    }

    private MockMultipartFile png() {
        return new MockMultipartFile("file", "face.png", "image/png", pngBytes());
    }

    private byte[] pngBytes() {
        return new byte[] {
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x00
        };
    }
}
