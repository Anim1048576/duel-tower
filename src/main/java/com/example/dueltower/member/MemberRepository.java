package com.example.dueltower.member;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Integer> {
    // 활성 회원만 조회/체크
    Optional<Member> findByUsernameAndDeletedFalse(String username);
    Optional<Member> findByIdAndDeletedFalse(Integer id);
    Page<Member> findAllByDeletedFalse(Pageable pageable);

    boolean existsByUsernameAndDeletedFalse(String username);
    boolean existsByEmailAndDeletedFalse(String email);

    // 필요하면 "탈퇴 포함" 조회용
    Optional<Member> findByUsername(String username);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
}
