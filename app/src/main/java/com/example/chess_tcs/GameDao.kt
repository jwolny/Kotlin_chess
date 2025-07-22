package com.example.chess_tcs

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface GameDao {
    @Insert
    suspend fun insertGame(game: GameEntity)

    @Query("SELECT * FROM games")
    fun getAllGames(): List<GameEntity>
}