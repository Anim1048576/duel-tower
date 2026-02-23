package com.example.dueltower.member;

import com.example.base.BaseUtility;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.sql.Timestamp;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "members")
public class Member {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.PROTECTED)
    private Integer id;

    @Column(nullable = false, length = 50, unique = true)
    private String username;

    @Column(nullable = false, length = 100)
    private String password;

    @Column(nullable = false, length = 100, unique = true)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoleType role;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Timestamp createDate;

    /**
     * 소프트 삭제 플래그
     * - true면 "탈퇴(삭제) 처리된 계정"으로 간주
     */
    @Column(nullable = false, columnDefinition = "TINYINT(1) DEFAULT 0")
    private boolean deleted;

    /** 삭제 처리 시각(soft delete 시각) */
    private Timestamp deletedDate;

    /**
     * 소프트 삭제 처리
     * - deleted=true
     * - deletedDate=now
     */
    public void softDelete() {
        this.deleted = true;
        this.deletedDate = new Timestamp(System.currentTimeMillis());
    }

    @Override
    public String toString() {
        String deletedDateStr = (deletedDate == null)
                ? "null"
                : BaseUtility.formatTimestamp(deletedDate, "yyyy-mm-dd");
        return "[Member]: {[id: " + id +
                "][username: " + username +
                "][email: " + email +
                "][role: " + role +
                "][createDate: " + BaseUtility.formatTimestamp(createDate, "yyyy-mm-dd") +
                "][deleted: " + deleted +
                "][deletedDate: " + deletedDateStr + "]}";
    }
}
