package ubc.cosc322;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class AI_AmazonGame {

    public static final int EMPTY = 0;


    public static final int BLACK = 1;
    public static final int WHITE = 2;

    public static final int ARROW = 3;

    private static final int BOARD_SIZE = 11;

    private int[][] board;

    public AI_AmazonGame() {
        board = new int[BOARD_SIZE][BOARD_SIZE];
    }

    public int[][] getBoard() {
        return board;
    }

    public void updateBoard(ArrayList<Integer> gamePosition) {
        if (gamePosition == null) {
            throw new IllegalArgumentException("gamePosition cannot be null");
        }

        if (gamePosition.size() != BOARD_SIZE * BOARD_SIZE) {
            throw new IllegalArgumentException(
                "Expected " + (BOARD_SIZE * BOARD_SIZE) + " board entries, got " + gamePosition.size()
            );
        }

        for (int i = 0; i < BOARD_SIZE; i++) {
            for (int j = 0; j < BOARD_SIZE; j++) {
                board[i][j] = gamePosition.get(i * BOARD_SIZE + j);
            }
        }
    }

    public static class Move {
        int[] queenPast;
        int[] queenFuture;
        int[] arrow;

        public Move(int[] queenPast, int[] queenFuture, int[] arrow) {
            this.queenPast = queenPast;
            this.queenFuture = queenFuture;
            this.arrow = arrow;
        }

        @Override
        public String toString() {
            return "Q(" + queenPast[0] + "," + queenPast[1] + ")"
                 + "->(" + queenFuture[0] + "," + queenFuture[1] + ")"
                 + " A->(" + arrow[0] + "," + arrow[1] + ")";
        }
    }

    public static final int[][] DIRECTIONS = {
        {-1,  0}, {1,  0},
        { 0, -1}, {0,  1},
        {-1, -1}, {-1, 1},
        { 1, -1}, {1,  1}
    };

    public boolean insideBoard(int row, int col) {
        return row >= 1 && row < BOARD_SIZE && col >= 1 && col < BOARD_SIZE;
    }

    public int[][] copyBoard(int[][] original) {
        int[][] copy = new int[BOARD_SIZE][BOARD_SIZE];
        for (int i = 0; i < BOARD_SIZE; i++) {
            System.arraycopy(original[i], 0, copy[i], 0, BOARD_SIZE);
        }
        return copy;
    }

    public void applyMove(int[][] state, Move m, int player) {
        if (!isLegalMove(state, m, player)) {
            throw new IllegalArgumentException("Illegal move attempted: " + m);
        }

        state[m.queenPast[0]][m.queenPast[1]] = EMPTY;
        state[m.queenFuture[0]][m.queenFuture[1]] = player;
        state[m.arrow[0]][m.arrow[1]] = ARROW;
    }

    public boolean isLegalMove(int[][] state, Move m, int player) {
        if (state == null || m == null) return false;

        int r1 = m.queenPast[0];
        int c1 = m.queenPast[1];
        int r2 = m.queenFuture[0];
        int c2 = m.queenFuture[1];
        int ar = m.arrow[0];
        int ac = m.arrow[1];

        if (!insideBoard(r1, c1) || !insideBoard(r2, c2) || !insideBoard(ar, ac)) {
            return false;
        }

        if (state[r1][c1] != player) {
            return false;
        }

        if (state[r2][c2] != EMPTY) {
            return false;
        }

        if (!isStraightOrDiagonal(r1, c1, r2, c2)) {
            return false;
        }

        if (!isClearPath(state, r1, c1, r2, c2)) {
            return false;
        }

        int[][] temp = copyBoard(state);
        temp[r1][c1] = EMPTY;
        temp[r2][c2] = player;

        if (temp[ar][ac] != EMPTY && !(ar == r1 && ac == c1)) {
            return false;
        }

        if (!isStraightOrDiagonal(r2, c2, ar, ac)) {
            return false;
        }

        if (!isClearPath(temp, r2, c2, ar, ac)) {
            return false;
        }

        return true;
    }

    private boolean isStraightOrDiagonal(int r1, int c1, int r2, int c2) {
        int dr = Math.abs(r2 - r1);
        int dc = Math.abs(c2 - c1);
        return (r1 == r2) || (c1 == c2) || (dr == dc);
    }

    private boolean isClearPath(int[][] state, int r1, int c1, int r2, int c2) {
        int dr = Integer.compare(r2, r1);
        int dc = Integer.compare(c2, c1);

        int cr = r1 + dr;
        int cc = c1 + dc;

        while (cr != r2 || cc != c2) {
            if (!insideBoard(cr, cc)) return false;
            if (state[cr][cc] != EMPTY) return false;
            cr += dr;
            cc += dc;
        }

        return true;
    }

    public List<Move> generateMoves(int[][] state, int player) {
        List<Move> moves = new ArrayList<>();

        for (int r = 1; r < BOARD_SIZE; r++) {
            for (int c = 1; c < BOARD_SIZE; c++) {
                if (state[r][c] != player) continue;

                for (int[] d : DIRECTIONS) {
                    int newrow = r + d[0];
                    int newcol = c + d[1];

                    while (insideBoard(newrow, newcol) && state[newrow][newcol] == EMPTY) {
                        for (int[] arrowdirection : DIRECTIONS) {
                            int arrowrow = newrow + arrowdirection[0];
                            int arrowcol = newcol + arrowdirection[1];

                            while (insideBoard(arrowrow, arrowcol)
                                    && (state[arrowrow][arrowcol] == EMPTY || (arrowrow == r && arrowcol == c))) {

                                Move move = new Move(
                                    new int[]{r, c},
                                    new int[]{newrow, newcol},
                                    new int[]{arrowrow, arrowcol}
                                );

                                if (isLegalMove(state, move, player)) {
                                    moves.add(move);
                                }

                                arrowrow += arrowdirection[0];
                                arrowcol += arrowdirection[1];
                            }
                        }

                        newrow += d[0];
                        newcol += d[1];
                    }
                }
            }
        }

        return moves;
    }

    public int evaluateBoard(int[][] state, int myPlayer) {
        int opponent = (myPlayer == WHITE) ? BLACK : WHITE;

        int[][] myDist = bfsDistances(state, myPlayer);
        int[][] oppDist = bfsDistances(state, opponent);

        int myTerritory = 0;
        int oppTerritory = 0;

        for (int r = 1; r < BOARD_SIZE; r++) {
            for (int c = 1; c < BOARD_SIZE; c++) {
                if (state[r][c] != EMPTY) continue;

                int mydistance = myDist[r][c];
                int opponentdistance = oppDist[r][c];

                if (mydistance < opponentdistance) {
                    myTerritory++;
                } else if (opponentdistance < mydistance) {
                    oppTerritory++;
                }
            }
        }

        int myFreedom = queenFreedom(state, myPlayer);
        int oppFreedom = queenFreedom(state, opponent);

        int myCenter = centerControl(state, myPlayer);
        int oppCenter = centerControl(state, opponent);

        int myTrapped = trappedQueens(state, myPlayer);
        int oppTrapped = trappedQueens(state, opponent);

        return 4 * (myTerritory - oppTerritory)
             + 2 * (myFreedom - oppFreedom)
             + 1 * (myCenter - oppCenter)
             - 12 * (myTrapped - oppTrapped);
    }

    private int queenFreedom(int[][] state, int player) {
        int total = 0;

        for (int r = 1; r < BOARD_SIZE; r++) {
            for (int c = 1; c < BOARD_SIZE; c++) {
                if (state[r][c] != player) continue;

                for (int[] d : DIRECTIONS) {
                    int nr = r + d[0];
                    int nc = c + d[1];

                    while (insideBoard(nr, nc) && state[nr][nc] == EMPTY) {
                        total++;
                        nr += d[0];
                        nc += d[1];
                    }
                }
            }
        }

        return total;
    }

    private int centerControl(int[][] state, int player) {
        int score = 0;

        for (int r = 1; r < BOARD_SIZE; r++) {
            for (int c = 1; c < BOARD_SIZE; c++) {
                if (state[r][c] != player) continue;

                int dist = Math.abs(r - 5) + Math.abs(c - 5);
                score += (10 - dist);
            }
        }

        return score;
    }

    private int trappedQueens(int[][] state, int player) {
        int trapped = 0;

        for (int r = 1; r < BOARD_SIZE; r++) {
            for (int c = 1; c < BOARD_SIZE; c++) {
                if (state[r][c] != player) continue;

                boolean canMove = false;

                for (int[] d : DIRECTIONS) {
                    int nr = r + d[0];
                    int nc = c + d[1];

                    if (insideBoard(nr, nc) && state[nr][nc] == EMPTY) {
                        canMove = true;
                        break;
                    }
                }

                if (!canMove) {
                    trapped++;
                }
            }
        }

        return trapped;
    }

    private int[][] bfsDistances(int[][] state, int player) {
        int[][] dist = new int[BOARD_SIZE][BOARD_SIZE];
        for (int[] row : dist) {
            Arrays.fill(row, Integer.MAX_VALUE);
        }

        Queue<int[]> queue = new LinkedList<>();
        boolean[][] visited = new boolean[BOARD_SIZE][BOARD_SIZE];

        for (int r = 1; r < BOARD_SIZE; r++) {
            for (int c = 1; c < BOARD_SIZE; c++) {
                if (state[r][c] == player) {
                    dist[r][c] = 0;
                    queue.add(new int[]{r, c});
                }
            }
        }

        while (!queue.isEmpty()) {
            int[] currentPosition = queue.poll();
            int currentRow = currentPosition[0];
            int currentCol = currentPosition[1];

            if (visited[currentRow][currentCol]) {
                continue;
            }
            visited[currentRow][currentCol] = true;

            for (int[] directions : DIRECTIONS) {
                int nextRow = currentRow + directions[0];
                int nextCol = currentCol + directions[1];

                while (insideBoard(nextRow, nextCol) && state[nextRow][nextCol] == EMPTY) {
                    int newDist = dist[currentRow][currentCol] + 1;
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