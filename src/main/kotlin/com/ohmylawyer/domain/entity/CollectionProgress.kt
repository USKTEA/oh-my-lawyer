package com.ohmylawyer.domain.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime
import java.util.UUID

enum class CollectionStatus {
    PENDING, QUEUED, RUNNING, COMPLETED, FAILED
}

@Entity
@Table(name = "collection_progress")
class CollectionProgress(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "task_type")
    val taskType: String,

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "data_type", columnDefinition = "document_type")
    val dataType: DocumentType,

    @Column(name = "total_count")
    var totalCount: Int = 0,

    @Column(name = "processed_count")
    var processedCount: Int = 0,

    @Column(name = "last_cursor")
    var lastCursor: String? = null,

    @Enumerated(EnumType.STRING)
    var status: CollectionStatus = CollectionStatus.PENDING,

    @Column(name = "error_message")
    var errorMessage: String? = null,

    @Column(name = "started_at")
    var startedAt: LocalDateTime? = null,

    @Column(name = "completed_at")
    var completedAt: LocalDateTime? = null,

    @Column(name = "updated_at")
    var updatedAt: LocalDateTime = LocalDateTime.now()
)
