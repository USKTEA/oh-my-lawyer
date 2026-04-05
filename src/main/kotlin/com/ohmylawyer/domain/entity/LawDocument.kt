package com.ohmylawyer.domain.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "law_documents")
class LawDocument(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "document_type")
    val type: DocumentType,

    val title: String,

    @Column(name = "full_text")
    val fullText: String,

    @Column(name = "source_url")
    val sourceUrl: String? = null,

    @Column(name = "source_id")
    val sourceId: String? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    val metadata: String = "{}",

    @Column(name = "enacted_date")
    val enactedDate: LocalDate? = null,

    @Column(name = "last_amended")
    val lastAmended: LocalDate? = null,

    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at")
    var updatedAt: LocalDateTime = LocalDateTime.now()
)
