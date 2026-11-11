package com.example.boschlaser.shared.command
import com.example.boschlaser.shared.model.*

data class AddBoardCommand(val board: Board) : SketchCommand {
    override fun apply(document: SketchDocument): SketchDocument? = if (board.id in document.boards) null else document.copy(boards = document.boards + (board.id to board))
}
data class UpdateBoardCommand(val board: Board) : SketchCommand {
    override fun apply(document: SketchDocument): SketchDocument? = if (board.id !in document.boards) null else document.copy(boards = document.boards + (board.id to board))
}
data class DeleteBoardCommand(val id: String) : SketchCommand {
    override fun apply(document: SketchDocument): SketchDocument? = if (id !in document.boards) null else document.copy(boards = document.boards - id)
}
