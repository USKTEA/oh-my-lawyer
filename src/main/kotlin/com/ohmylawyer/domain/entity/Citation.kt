package com.ohmylawyer.domain.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "citations")
class Citation(
    @Id
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_document_id")
    val sourceDocument: LawDocument,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_document_id")
    val targetDocument: LawDocument,

    @Column(name = "citation_type")
    val citationType: String = "REFERENCE",

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    val metadata: String = "{}",

    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now()
)
