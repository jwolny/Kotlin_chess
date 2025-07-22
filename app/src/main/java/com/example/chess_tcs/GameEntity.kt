package com.example.chess_tcs

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "games")
data class GameEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val pgn: String,
    val gameMode: String,
    val winner: String?,
)