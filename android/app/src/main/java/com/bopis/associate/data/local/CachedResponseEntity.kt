package com.bopis.associate.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

// Simple key/JSON-blob read cache (e.g. "orders", "order:<id>", "shelf:<qr>") so the app
// can show the last-known state when offline. Deliberately not a normalized mirror of the
// Postgres schema -- keeping this minimal is consistent with the project's simplicity goal.
@Entity(tableName = "cached_responses")
data class CachedResponseEntity(
    @PrimaryKey val key: String,
    val json: String,
    val updatedAt: Long,
)
