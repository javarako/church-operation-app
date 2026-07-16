package com.church.operation.service;

import com.church.operation.entity.Member;
import com.church.operation.entity.Offering;
import com.church.operation.entity.TaxReceipt;
import com.church.operation.entity.FiscalArchiveRegistry;
import com.church.operation.exception.DeletionBlockedException;
import com.church.operation.repo.MemberRepository;
import com.church.operation.repo.PasswordResetTokenRepository;
import com.church.operation.util.Role;
import com.church.operation.util.FiscalArchiveStatus;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class MemberDeletionService {
    private final MemberRepository memberRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final MemberImageService memberImageService;
    private final MongoTemplate mongoTemplate;

    public MemberDeletionService(
        MemberRepository memberRepository,
        PasswordResetTokenRepository tokenRepository,
        MemberImageService memberImageService,
        MongoTemplate mongoTemplate
    ) {
        this.memberRepository = memberRepository;
        this.tokenRepository = tokenRepository;
        this.memberImageService = memberImageService;
        this.mongoTemplate = mongoTemplate;
    }

    public void delete(Member actor, String id) {
        requireMembershipManager(actor);
        Member target = memberRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Member was not found."));

        if (Objects.equals(actor.getId(), target.getId())) {
            throw new DeletionBlockedException("The signed-in member cannot be deleted.");
        }
        if ("admin".equalsIgnoreCase(target.getPrimaryEmail())) {
            throw new DeletionBlockedException("The System Administrator cannot be deleted.");
        }

        List<String> dependencies = new ArrayList<>();
        Query memberReference = Query.query(Criteria.where("memberId").is(id));
        if (mongoTemplate.exists(memberReference, Offering.class)) {
            dependencies.add("offering records");
        }
        if (mongoTemplate.exists(memberReference, TaxReceipt.class)) {
            dependencies.add("official tax receipts");
        }
        Query archivedMember = Query.query(Criteria.where("memberIds").is(id)
            .and("status").is(FiscalArchiveStatus.CLEANED));
        if (mongoTemplate.exists(archivedMember, FiscalArchiveRegistry.class)) {
            dependencies.add("a cleaned fiscal archive");
        }
        if (!dependencies.isEmpty()) {
            throw new DeletionBlockedException(
                "This member cannot be deleted because it is referenced by " + String.join(" and ", dependencies)
                    + ". Make the member inactive or lock login instead."
            );
        }

        memberImageService.remove(actor, id);
        tokenRepository.deleteByMemberEmail(target.getPrimaryEmail());
        memberRepository.delete(target);
    }

    private void requireMembershipManager(Member actor) {
        if (!hasRole(actor, Role.ADMIN) && !hasRole(actor, Role.MEMBERSHIP)) {
            throw new SecurityException("You do not have permission to delete members.");
        }
    }

    private boolean hasRole(Member actor, Role role) {
        return actor != null && actor.getRoles() != null && actor.getRoles().contains(role);
    }
}
