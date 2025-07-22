package com.example.chess_tcs

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class GameHistoryAdapter(
    private val onItemClick: (GameEntity) -> Unit = {}
) : ListAdapter<GameEntity, GameHistoryAdapter.GameViewHolder>(GameDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GameViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_game_history, parent, false)
        return GameViewHolder(view, onItemClick)
    }

    override fun onBindViewHolder(holder: GameViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class GameViewHolder(view: View, private val onItemClick: (GameEntity) -> Unit) : RecyclerView.ViewHolder(view) {
        private val modeTextView: TextView = view.findViewById(R.id.textViewMode)
        private val resultTextView: TextView = view.findViewById(R.id.textViewResult)
        private val movesTextView: TextView = view.findViewById(R.id.textViewMoves)
        private val dateTextView: TextView = view.findViewById(R.id.textViewDate)

        fun bind(game: GameEntity) {
            itemView.setOnClickListener { onItemClick(game) }

            modeTextView.text = "Tryb:  ${game.gameMode.replace("_", " ")}"
            resultTextView.text = when (game.winner) {
                "WHITE" -> "    Wygrana białych"
                "BLACK" -> "    Wygrana czarnych"
                "DRAW" -> " Remis"
                else -> "   Nie zakończono"
            }

            movesTextView.text = formatMoves(game.pgn)

        }

        private fun formatMoves(pgn: String): String {
            return try {
                pgn.split(" ")
                    .take(10) // Tylko pierwsze 10!
                    .joinToString(" ") + if (pgn.split(" ").size > 10) "..." else ""
            } catch (e: Exception) {
                "Brak danych o ruchach"
            }
        }
    }
}


// Pomocnicze: gdyby mialy sie zmieniac te recordy, to potrzebne sa takie funkcje
class GameDiffCallback : DiffUtil.ItemCallback<GameEntity>() {
    override fun areItemsTheSame(oldItem: GameEntity, newItem: GameEntity): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: GameEntity, newItem: GameEntity): Boolean {
        return oldItem == newItem
    }
}