package com.example.chess_tcs

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.Toast

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val buttonLocalGame = findViewById<Button>(R.id.button_local_game)
        val buttonBluetoothGame = findViewById<Button>(R.id.button_bluetooth_game)
        val buttonAIGame = findViewById<Button>(R.id.button_ai_game)
        val buttonHistory = findViewById<Button>(R.id.button_history)

        buttonLocalGame.setOnClickListener {
            val intent = Intent(this, LocalGameActivity::class.java)
            intent.putExtra("GAME_MODE", "LOCAL")
            startActivity(intent)
        }

        buttonBluetoothGame.setOnClickListener {
            val intent = Intent(this, BluetoothDevicesActivity::class.java)
            startActivity(intent)
        }

        buttonAIGame.setOnClickListener {
            val intent = Intent(this, LocalGameActivity::class.java)
            intent.putExtra("GAME_MODE", "AI")
            startActivity(intent)
        }

        buttonHistory.setOnClickListener {
            startActivity(Intent(this, GameHistoryActivity::class.java))
        }
    }
}
