package ubc.cosc322;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;


public class AI_AmazonGame {

    // Cell content constants — used throughout instead of raw integers for clarity
    public static final int EMPTY = 0; // unoccupied cell
    public static final int WHITE = 1; // Player 1's queen
    public static final int BLACK = 2; // Player 2's queen
    public static final int ARROW = 3; // fired arrow (permanently blocks the cell)

    // The server sends an 11x11 grid where row/col 0 is padding — real board is 1..10
    int Board_size = 11;

    // The 2D board array — board[row][col] holds one of the four constants above
    int[][] board;

    public AI_AmazonGame() {
        board = new int[Board_size][Board_size];
    }

    /** Returns a direct reference to the live board — used by Minimax and the client. */
    public int[][] getBoard() {
        return board;
    }

    /**
     * Converts to 2D board array.
     * The server sends row-major order: index = row * 11 + col.
     */
    public void updateBoard(ArrayList<Integer> gamePosition) {
        for (int i = 0; i < Board_size; i++) {
            for (int j = 0; j < Board_size; j++) {
                board[i][j] = gamePosition.get(i * Board_size + j);
            }
        }
    }

    
    public void applyMove(int[][] state, Move m, int player) {
        state[m.queenPast[0]][m.queenPast[1]]     = EMPTY;  // vacate origin
        state[m.queenFuture[0]][m.queenFuture[1]] = player; // land queen
        state[m.arrow[0]][m.arrow[1]]             = ARROW;  // block arrow cell
    }

    public static class Move {
        int[] queenPast;
        int[] queenFuture;
        int[] arrow;

        public Move(int[] queenPast, int[] queenFuture, int[] arrow) {
            this.queenPast   = queenPast;
            this.queenFuture = queenFuture;
            this.arrow       = arrow;
        }

        /** Human-readable form used in debug output and error messages. */
        @Override
        public String toString() {
            return "Q(" + queenPast[0] + "," + queenPast[1] + ")"
                 + "->(" + queenFuture[0] + "," + queenFuture[1] + ")"
                 + " A->(" + arrow[0] + "," + arrow[1] + ")";
        }
    }

 
    public static final int[][] DIRECTIONS = {
        {-1,  0}, { 1,  0},  // up, down
        { 0, -1}, { 0,  1},  // left, right
        {-1, -1}, {-1,  1},  // diagonal: up-left, up-right
        { 1, -1}, { 1,  1}   // diagonal: down-left, down-right
    };

    
    public List<Move> generateMoves(int[][] state, int player) {
        List<Move> moves = new ArrayList<>();

        // Scan every cell on the real board (indices 1..10, skipping the padding row/col 0)
        for (int r = 1; r < Board_size; r++) {
            for (int c = 1; c < Board_size; c++) {

                // Only process cells that hold one of our queens
                if (state[r][c] != player) continue;

                // Try sliding the queen in each of the 8 directions
                for (int[] d : DIRECTIONS) {
                    int newrow = r + d[0];
                    int newcol = c + d[1];

                    // Keep going until we hit a wall or an occupied cell
                    while (insideBoard(newrow, newcol) && state[newrow][newcol] == EMPTY) {

                        // From this queen destination, try firing the arrow in all 8 directions
                        for (int[] arrowdirection : DIRECTIONS) {
                            int arrowrow = newrow + arrowdirection[0];
                            int arrowcol = newcol + arrowdirection[1];

                            // Arrow slides like a queen — can also pass through the queen's vacated origin
                            while (insideBoard(arrowrow, arrowcol)
                                    && (state[arrowrow][arrowcol] == EMPTY
                                        || (arrowrow == r && arrowcol == c))) { // origin is now empty

                                // This combination of queen move + arrow position is a valid move
                                moves.add(new Move(
                                    new int[]{r, c},            // queen origin
                                    new int[]{newrow, newcol},  // queen destination
                                    new int[]{arrowrow, arrowcol} // arrow landing
                                ));

                                // Continue arrow in the same direction
                                arrowrow += arrowdirection[0];
                                arrowcol += arrowdirection[1];
                            }
                        }

                        // Continue queen in the same direction
                        newrow += d[0];
                        newcol += d[1];
                    }
                }
            }
        }
        return moves;
    }

    public int[][] copyBoard(int[][] original) {
        int[][] copy = new int[Board_size][Board_size];
        for (int i = 0; i < Board_size; i++) {
            // arraycopy is faster than a manual loop for row-by-row copying
            System.arraycopy(original[i], 0, copy[i], 0, Board_size);
        }
        return copy;
    }

    /**
     * Returns true if (row, col) is within the playable board area.
     * Starts at 1 (not 0) because index 0 is padding sent by the server
     * and does not represent a real cell.
     */
    public boolean insideBoard(int row, int col) {
        return row >= 1 && row < Board_size && col >= 1 && col < Board_size;
    }

   
    public int evaluateBoard(int[][] state, int myPlayer) {

        // Determine the opponent based on our color
        int opponent;
        if (myPlayer == WHITE) {
            opponent = BLACK;
        } else {
            opponent = WHITE;
        }

        // BFS from all of each player's queens simultaneously
        // Each cell gets the minimum queen-move distance from that player's queens
        int[][] myDist  = bfsDistances(state, myPlayer);
        int[][] oppDist = bfsDistances(state, opponent);

        int myTerritory  = 0; // cells we reach before the opponent
        int oppTerritory = 0; // cells the opponent reaches before us
        int mobilityMy   = 0; // cells directly adjacent (distance 1) to our queens
        int mobilityOpp  = 0; // cells directly adjacent (distance 1) to opponent's queens

        // Compare BFS distances cell by cell to assign territory and count mobility
        for (int r = 1; r < Board_size; r++) {
            for (int c = 1; c < Board_size; c++) {
                int mydistance  = myDist[r][c];
                int oppdistance = oppDist[r][c];

                // Whoever can reach this cell in fewer moves "owns" it
                if (mydistance < oppdistance) {
                    myTerritory++;
                } else if (oppdistance < mydistance) {
                    oppTerritory++;
                }

                // Distance 1 means directly reachable in one queen move — counts as mobility
                if (mydistance == 1)  mobilityMy++;
                if (oppdistance == 1) mobilityOpp++;
            }
        }

        // Combine factors — territory is weighted higher because it determines
        // long-term winning chances, while mobility is a more immediate indicator
        return 2 * (myTerritory - oppTerritory)
             + 1 * (mobilityMy  - mobilityOpp);
    }

    
    private int[][] bfsDistances(int[][] state, int player) {

        // Initialize all distances to infinity — will be overwritten during BFS
        int[][] dist = new int[Board_size][Board_size];
        for (int[] row : dist) {
            java.util.Arrays.fill(row, Integer.MAX_VALUE);
        }

        Queue<int[]> queue = new LinkedList<>();

        // Seed the BFS from every queen belonging to this player (distance = 0)
        for (int r = 1; r < Board_size; r++) {
            for (int c = 1; c < Board_size; c++) {
                if (state[r][c] == player) {
                    dist[r][c] = 0;
                    queue.add(new int[]{r, c});
                }
            }
        }

        // visited array prevents re-processing cells that are already settled
        boolean[][] visited = new boolean[Board_size][Board_size];

        while (!queue.isEmpty()) {
            int[] currentPosition = queue.poll();
            int currentRow = currentPosition[0];
            int currentCol = currentPosition[1];

            // Skip if this cell's shortest distance was already finalized
            if (visited[currentRow][currentCol]) {
                continue;
            }
            visited[currentRow][currentCol] = true;

            // From this cell, expand in all 8 directions (queen-slide movement)
            for (int[] directions : DIRECTIONS) {
                int nextRow = currentRow + directions[0];
                int nextCol = currentCol + directions[1];

                // Keep going in this direction while the path is clear
                while (insideBoard(nextRow, nextCol) && state[nextRow][nextCol] == EMPTY) {
                    int newDist = dist[currentRow][currentCol] + 1;

                    // Only enqueue if we found a shorter path to this cell
                    if (newDist < dist[nextRow][nextCol]) {
                        dist[nextRow][nextCol] = newDist;
                        queue.add(new int[]{nextRow, nextCol});
                    }

                    nextRow += directions[0];
                    nextCol += directions[1];
                }
            }
        }

        return dist;
    }
}