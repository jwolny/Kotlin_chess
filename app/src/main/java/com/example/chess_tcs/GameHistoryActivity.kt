package com.example.chess_tcs

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class GameHistoryActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: GameHistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game_history)

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = GameHistoryAdapter()
        recyclerView.adapter = adapter

        loadGames()
    }

    private fun loadGames() {
        val database = AppDatabase.getDatabase(this)
        GlobalScope.launch(Dispatchers.IO) {
            val games = database.gameDao().getAllGames()
            runOnUiThread {
                adapter.submitList(games)
            }
        }
    }
}