package com.example.chess_tcs

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.PieceType
import com.github.bhlangonijr.chesslib.move.Move
import com.github.bhlangonijr.chesslib.Square
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import androidx.appcompat.app.AlertDialog
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.Rank
import com.github.bhlangonijr.chesslib.Side


data class Result(
    val whiteWon: Boolean = false,
    val blackWon: Boolean = false,
    val draw: Boolean = false
)

class LocalGameActivity : AppCompatActivity() {
    private lateinit var boardLayout: GridLayout
    private lateinit var chessBoard: Board
    private var selectedSquare: Square? = null
    private lateinit var highlightViews: MutableList<View>
    private val pieceViews = mutableMapOf<Square, ImageView>()

    // AI
    private var isPlayerTurn = true
    private var engineThinking = false
    private val client = OkHttpClient()
    private var pendingPromotionMove: Move? = null
    private val movesHistory = mutableListOf<Move>()


    // Bluetooth
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null
    private var connectedThread: ConnectedThread? = null
    private var bluetoothServerSocket: BluetoothServerSocket? = null
    private var acceptThread: AcceptThread? = null

    // Baza
    private lateinit var database: AppDatabase
    private lateinit var gameDao: GameDao


    // Tryb gry
    private enum class GameMode { LOCAL, AI, BLUETOOTH }
    private lateinit var gameMode: GameMode
    private var isHost = false

    companion object {
        private const val REQUEST_ENABLE_BT = 1
        private const val UUID_STRING = "00001101-0000-1000-8000-00805F9B34FB" // Standardowy UUID
    }

    private inner class AcceptThread : Thread() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun run() {
            try {
                bluetoothServerSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord("ChessGame", UUID.fromString(UUID_STRING))
                val socket = bluetoothServerSocket?.accept() // blokujące czekanie hosta

                if (socket != null) {
                    bluetoothSocket = socket
                    outputStream = socket.outputStream
                    inputStream = socket.inputStream

                    connectedThread = ConnectedThread()
                    connectedThread?.start()

                    runOnUiThread {
                        Toast.makeText(this@LocalGameActivity, "Połączono z przeciwnikiem", Toast.LENGTH_SHORT).show()
                        setupTouchListener()
                        // Host zawsze zaczyna
                        isPlayerTurn = true
                    }

                    bluetoothServerSocket?.close()
                }
            } catch (e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@LocalGameActivity, "Błąd podczas oczekiwania na połączenie: ${e.message}", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }

        fun cancel() {
            try {
                bluetoothServerSocket?.close()
            } catch (_: IOException) {
            }
        }
    }


    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_local_game)

        gameMode = when (intent.getStringExtra("GAME_MODE")) {
            "AI" -> GameMode.AI
            "BLUETOOTH" -> GameMode.BLUETOOTH
            else -> GameMode.LOCAL
        }

        database = AppDatabase.getDatabase(this)
        gameDao = database.gameDao()

        boardLayout = findViewById(R.id.game_frame)
        chessBoard = Board()

        setupBoard()
        drawPieces()

        if (gameMode == GameMode.BLUETOOTH) {
            setupBluetooth()
        } else {
            setupTouchListener()
        }
    }


    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun setupBluetooth() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Urządzenie nie obsługuje Bluetooth", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
        } else {
            startBluetoothConnection()
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun startBluetoothConnection() {
        val deviceAddress = intent.getStringExtra("DEVICE_ADDRESS")
        isHost = deviceAddress == null // host nie ma adresu wpisanego

        if (isHost) {
            Toast.makeText(this, "Oczekiwanie na połączenie...", Toast.LENGTH_SHORT).show()
            acceptThread = AcceptThread()
            acceptThread?.start()
        } else {
            val device = bluetoothAdapter.getRemoteDevice(deviceAddress)
            connectToDevice(device)
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun connectToDevice(device: BluetoothDevice) {
        try {
            val uuid = UUID.fromString(UUID_STRING)
            bluetoothSocket = device.createRfcommSocketToServiceRecord(uuid)
            bluetoothSocket?.connect()

            outputStream = bluetoothSocket?.outputStream
            inputStream = bluetoothSocket?.inputStream

            connectedThread = ConnectedThread()
            connectedThread?.start()

            setupTouchListener()
            Toast.makeText(this, "Połączono z ${device.name}", Toast.LENGTH_SHORT).show()

            isPlayerTurn = !isHost
            if (!isPlayerTurn) {
                Toast.makeText(this, "Czekaj na ruch przeciwnika...", Toast.LENGTH_SHORT).show()
            }
        } catch (e: IOException) {
            Toast.makeText(this, "Błąd połączenia: ${e.message}", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private inner class ConnectedThread : Thread() {
        override fun run() {
            val buffer = ByteArray(1024)
            var bytes: Int

            while (true) {
                try {
                    bytes = inputStream?.read(buffer) ?: 0
                    val receivedMessage = String(buffer, 0, bytes)
                    runOnUiThread {
                        handleReceivedMove(receivedMessage)
                    }
                } catch (e: IOException) {
                    runOnUiThread {
                        Toast.makeText(this@LocalGameActivity, "Utracono połączenie", Toast.LENGTH_SHORT).show()
                    }
                    break
                }
            }
        }
    }

    private fun handleReceivedMove(moveString: String) {
        try {
            val move = Move(moveString, chessBoard.sideToMove)
            if (chessBoard.legalMoves().contains(move)) {
                chessBoard.doMove(move)
                movesHistory.add(move)
                drawPieces()

                isPlayerTurn = true
                if (chessBoard.isMated || chessBoard.isDraw) {
                    onGameEnd()
                } else {
                    Toast.makeText(this, "Twój ruch", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Nieprawidłowy ruch od przeciwnika", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendMove(move: Move) {
        try {
            outputStream?.write(move.toString().toByteArray())
            isPlayerTurn = false
            Toast.makeText(this, "Czekaj na ruch przeciwnika...", Toast.LENGTH_SHORT).show()
        } catch (e: IOException) {
            Toast.makeText(this, "Błąd wysyłania ruchu", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouchListener() {
        highlightViews = mutableListOf()

        boardLayout.setOnTouchListener { _, event ->
            if (!isPlayerTurn || engineThinking) return@setOnTouchListener true

            val col = (event.x / (boardLayout.width / 8)).toInt()
            val row = (event.y / (boardLayout.height / 8)).toInt()
            val clickedSquare = getSquareFromCoordinates(col, row)

            if (selectedSquare == null) {
                val piece = chessBoard.getPiece(clickedSquare)
                if (piece != null && piece.pieceSide == chessBoard.sideToMove) {
                    selectedSquare = clickedSquare
                    highlightAvailableMoves()
                }
            } else {
                if (clickedSquare == selectedSquare) {
                    selectedSquare = null
                    clearHighlights()
                } else {
                    val move = Move(selectedSquare!!, clickedSquare)
                    if(isPromotionMove(move)){
                        pendingPromotionMove = move
                        showPromotionDialog()
                    } else if(chessBoard.legalMoves().contains(move)){
                        executeMove(move)
                    }
                }
            }
            true
        }
    }

    private fun isPromotionMove(move: Move): Boolean {
        val piece = chessBoard.getPiece(move.from) ?: return false
        if (piece.pieceType != PieceType.PAWN) {
            return false
        }

        val targetRank = move.to.rank
        val isWhite = piece.pieceSide == Side.WHITE
        val isPromotionRank = (isWhite && targetRank == Rank.RANK_8) ||
                (!isWhite && targetRank == Rank.RANK_1)
        return isPromotionRank
    }

    private fun showPromotionDialog() {
        val side = chessBoard.sideToMove
        val promotions = listOf(
            Piece.make(side, PieceType.QUEEN),
            Piece.make(side, PieceType.ROOK),
            Piece.make(side, PieceType.BISHOP),
            Piece.make(side, PieceType.KNIGHT)
        )

        val pieceNames = promotions.map {
            when (it.pieceType) {
                PieceType.QUEEN -> "Hetman"
                PieceType.ROOK -> "Wieża"
                PieceType.BISHOP -> "Goniec"
                PieceType.KNIGHT -> "Skoczek"
                else -> it.pieceType?.name?.toLowerCase()?.capitalize()
            }
        }

        val builder = AlertDialog.Builder(this)
        builder.setTitle("Wybierz promocję pionka")
            .setItems(pieceNames.toTypedArray()) { _, which ->
                pendingPromotionMove?.let { originalMove ->
                    val promotedMove = Move(
                        originalMove.from,
                        originalMove.to,
                        promotions[which]
                    )
                    executeMove(promotedMove)
                    pendingPromotionMove = null
                }
            }
            .setCancelable(false)
            .create()
            .show()
    }

    private fun executeMove(move: Move) {
        pieceViews[selectedSquare!!]?.let {
            boardLayout.removeView(it)
            pieceViews.remove(selectedSquare!!)
        }

        chessBoard.doMove(move)
        movesHistory.add(move)
        drawPieces()

        selectedSquare = null
        clearHighlights()

        if (chessBoard.isMated || chessBoard.isDraw) {
            onGameEnd()
            return
        }

        when (gameMode) {
            GameMode.AI -> {
                if (!chessBoard.isMated && !chessBoard.isDraw) {
                    isPlayerTurn = false
                    makeEngineMove()
                }
            }
            GameMode.BLUETOOTH -> {
                sendMove(move)
            }
            else -> { /* LOCAL to po prostu druga strona */ }
        }
    }

    private fun makeEngineMove() {
        if (gameMode != GameMode.AI) return

        engineThinking = true

        EngineApi.makeEngineMoveUsingApi(
            fen = chessBoard.fen,
            client = client
        ) { moveString ->
            runOnUiThread {
                if (moveString == null) {
                    Toast.makeText(this@LocalGameActivity, "Błąd silnika", Toast.LENGTH_SHORT).show()
                    engineThinking = false
                    isPlayerTurn = true
                    return@runOnUiThread
                }

                try {
                    val move = Move(moveString, chessBoard.sideToMove)
                    if (chessBoard.legalMoves().contains(move)) {
                        chessBoard.doMove(move)
                        movesHistory.add(move)
                        drawPieces()

                        if (chessBoard.isMated || chessBoard.isDraw) {
                            onGameEnd()
                        }
                    } else {
                        Toast.makeText(this@LocalGameActivity, "Silnik zaproponował nielegalny ruch", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@LocalGameActivity, "Błąd wykonania ruchu silnika", Toast.LENGTH_SHORT).show()
                } finally {
                    engineThinking = false
                    isPlayerTurn = true
                }
            }
        }
    }

    private fun setupBoard() {
        boardLayout.rowCount = 8
        boardLayout.columnCount = 8

        for (i in 0 until 8) {
            for (j in 0 until 8) {
                val squareView = View(this)
                squareView.layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = 0
                    columnSpec = GridLayout.spec(j, 1f)
                    rowSpec = GridLayout.spec(i, 1f)
                }
                squareView.setBackgroundColor(
                    if ((i + j) % 2 == 0) getColor(R.color.white) else getColor(R.color.teal_700)
                )
                boardLayout.addView(squareView)
            }
        }
    }

    private fun getPieceDrawable(piece: String): Int? {
        return when (piece) {
            "WHITE_PAWN" -> R.drawable.pawn
            "WHITE_ROOK" -> R.drawable.rook
            "WHITE_KNIGHT" -> R.drawable.knight
            "WHITE_BISHOP" -> R.drawable.wbis
            "WHITE_QUEEN" -> R.drawable.queen
            "WHITE_KING" -> R.drawable.king
            "BLACK_PAWN" -> R.drawable.bpawn
            "BLACK_ROOK" -> R.drawable.brook
            "BLACK_KNIGHT" -> R.drawable.bknight
            "BLACK_BISHOP" -> R.drawable.bbis
            "BLACK_QUEEN" -> R.drawable.bqueen
            "BLACK_KING" -> R.drawable.bking
            else -> null
        }
    }

    private fun drawPieces() {
        pieceViews.values.forEach { boardLayout.removeView(it) }
        pieceViews.clear()

        for (row in 0 until 8) {
            for (col in 0 until 8) {
                val square = Square.values().first { it.ordinal == (7 - row) * 8 + col }
                val piece = chessBoard.getPiece(square)

                if (piece != null) {
                    val pieceView = ImageView(this)
                    val resId = getPieceDrawable(piece.toString())
                    if (resId != null) {
                        pieceView.setImageResource(resId)
                        pieceView.layoutParams = GridLayout.LayoutParams().apply {
                            width = 0
                            height = 0
                            columnSpec = GridLayout.spec(col, 1f)
                            rowSpec = GridLayout.spec(row, 1f)
                        }
                        boardLayout.addView(pieceView)
                        pieceViews[square] = pieceView
                    }
                }
            }
        }
    }

    private fun highlightAvailableMoves() {
        clearHighlights()

        val availableMoves = chessBoard.legalMoves().filter { it.from == selectedSquare }
        for (move in availableMoves) {
            val targetSquare = move.to
            val highlightRow = 7 - targetSquare.rank.ordinal
            val highlightCol = targetSquare.file.ordinal

            val highlightView = View(this)
            highlightView.layoutParams = GridLayout.LayoutParams().apply {
                width = 0
                height = 0
                columnSpec = GridLayout.spec(highlightCol, 1f)
                rowSpec = GridLayout.spec(highlightRow, 1f)
            }
            highlightView.setBackgroundColor(getColor(R.color.purple_500))
            boardLayout.addView(highlightView)
            highlightViews.add(highlightView)
        }
    }

    private fun clearHighlights() {
        for (highlightView in highlightViews) {
            boardLayout.removeView(highlightView)
        }
        highlightViews.clear()
    }

    private fun getSquareFromCoordinates(col: Int, row: Int): Square {
        val realRow = 7 - row
        return Square.squareAt(realRow * 8 + col)
    }

    private fun onGameEnd() {
        val result = when {
            chessBoard.isMated -> {
                if (chessBoard.sideToMove.equals(Side.WHITE)) Result(blackWon = true)
                else Result(whiteWon = true)
            }
            chessBoard.isDraw -> Result(draw = true)
            else -> return
        }

        val winner = when {
            result.whiteWon -> "WHITE"
            result.blackWon -> "BLACK"
            else -> "DRAW"
        }

        val pgn = movesHistory.toString()

        lifecycleScope.launch(Dispatchers.IO) {
            val game = GameEntity(
                pgn = pgn,
                gameMode = gameMode.name,
                winner = winner
            )
            database.gameDao().insertGame(game)
        }

        val message = when {
            result.whiteWon -> "Białe wygrywają przez szachmat!"
            result.blackWon -> "Czarne wygrywają przez szachmat!"
            result.draw -> when {
                chessBoard.isStaleMate -> "Pat! Remis!"
                chessBoard.isRepetition -> "Remis przez powtórzenie!"
                chessBoard.isInsufficientMaterial -> "Remis - brak materiału!"
                else -> "Remis!"
            }
            else -> "Koniec gry"
        }

        runOnUiThread {
            Toast.makeText(this@LocalGameActivity, message, Toast.LENGTH_LONG).show()

            Handler(Looper.getMainLooper()).postDelayed({
                AlertDialog.Builder(this@LocalGameActivity)
                    .setTitle("Koniec gry")
                    .setMessage(message)
                    .setPositiveButton("Powrót do menu") { _, _ ->
                        val intent = Intent(this@LocalGameActivity, MainActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                        startActivity(intent)
                        finish()
                    }
                    .setCancelable(false)
                    .show()
            }, 2000)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        acceptThread?.cancel()
        connectedThread?.interrupt()
        bluetoothSocket?.close()
    }
}

object EngineApi {
    fun makeEngineMoveUsingApi(fen: String, client: OkHttpClient, callback: (String?) -> Unit) {
        val apiUrl = "https://stockfish.online/api/s/v2.php?fen=${fen}&depth=5"

        val request = Request.Builder()
            .url(apiUrl)
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("EngineApi", "Błąd połączenia z API: ${e.message}")
                callback(null)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        Log.e("EngineApi", "Błąd odpowiedzi: HTTP ${response.code}")
                        callback(null)
                        return
                    }

                    val responseText = response.body?.string()
                    Log.d("EngineApi", "Odpowiedź: $responseText")

                    try {
                        val json = JSONObject(responseText)
                        val bestMove = json.optString("bestmove", null)
                        val parts = bestMove.split(" ")
                        callback(parts.getOrNull(1))
                    } catch (e: Exception) {
                        Log.e("EngineApi", "Błąd parsowania JSON: ${e.message}")
                        callback(null)
                    }
                }
            }
        })
    }
}