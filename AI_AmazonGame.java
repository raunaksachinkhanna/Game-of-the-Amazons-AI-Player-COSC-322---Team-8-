package ubc.cosc322;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;


public class AI_AmazonGame {

    // Cell content constants — used throughout instead of raw integers for clarity
    public static final int EMPTY = 0; // unoccupied cell
    public static final int BLACK = 1; // Player 1's queen
    public static final int WHITE = 2; // Player 2's queen
    public static final int ARROW = 3; // fired arrow (permanently blocks the cell)
    private static final int BOARD_SIZE = 11;
    // The server sends an 11x11 grid where row/col 0 is padding — real board is 1..10

    private static final int WIN_SCORE  =  1_000_000;
    private static final int LOSS_SCORE = -1_000_000;
    // The 2D board array — board[row][col] holds one of the four constants above
    int[][] board;
    public enum QueenRole {
        AGGRESSIVE,
        DEFENSIVE,
        NEUTRAL
    }

    // A queen with freedom/pressure ratio above this is AGGRESSIVE
    private static final double AGGRESSION_THRESHOLD = 1.5;

    // A queen with freedom/pressure ratio below this is DEFENSIVE
    private static final double DEFENSE_THRESHOLD = 0.7;

    // Bonus per opponent cell we can block (AGGRESSIVE queens)
    private static final int AGGRESSIVE_BONUS_PER_BLOCKED = 3;

    // Bonus per escape route available (DEFENSIVE queens)
    private static final int DEFENSIVE_BONUS_PER_ESCAPE = 5;

    // Penalty when a DEFENSIVE queen has almost no escape routes (≤ 2)
    private static final int DEFENSIVE_STUCK_PENALTY = 8;

    public AI_AmazonGame() {
        board = new int[BOARD_SIZE][BOARD_SIZE];
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
        for (int r = 1; r < BOARD_SIZE; r++) {
            for (int c = 1; c < BOARD_SIZE; c++) {

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
        int[][] copy = new int[BOARD_SIZE][BOARD_SIZE];
        for (int i = 0; i < BOARD_SIZE; i++) {
            // arraycopy is faster than a manual loop for row-by-row copying
            System.arraycopy(original[i], 0, copy[i], 0, BOARD_SIZE);
        }
        return copy;
    }

    /**
     * Returns true if (row, col) is within the playable board area.
     * Starts at 1 (not 0) because index 0 is padding sent by the server
     * and does not represent a real cell.
     */
    public boolean insideBoard(int row, int col) {
        return row >= 1 && row < BOARD_SIZE && col >= 1 && col < BOARD_SIZE;
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
        
        boolean separated = true;
        for (int r = 1; r < BOARD_SIZE && separated; r++) {
            for (int c = 1; c < BOARD_SIZE && separated; c++) {
                if (myDist[r][c] != Integer.MAX_VALUE
                 && oppDist[r][c] != Integer.MAX_VALUE) {
                    separated = false;
                }
            }
        }
        
        if (separated) {
            int myMoves  = countMobility(state, myPlayer);
            int oppMoves = countMobility(state, opponent);
            if(myMoves == 0) return LOSS_SCORE;
            if(oppMoves == 0)return WIN_SCORE;
            //Since we are in the endgame stage we need best desicions
            return 100 * (myMoves - oppMoves);
        }

        int myTerritory  = 0; // cells we reach before the opponent
        int oppTerritory = 0; // cells the opponent reaches before us
        
        // Compare BFS distances cell by cell to assign territory and count mobility
        for (int r = 1; r < BOARD_SIZE; r++) {
            for (int c = 1; c < BOARD_SIZE; c++) {
            	if (state[r][c] != EMPTY) continue;
            	
                int mydistance  = myDist[r][c];
                int oppdistance = oppDist[r][c];

                // Whoever can reach this cell in fewer moves "owns" it
                if (mydistance < oppdistance && mydistance != Integer.MAX_VALUE) {
                    myTerritory += (10 - mydistance); 
                } else if (oppdistance < mydistance && oppdistance != Integer.MAX_VALUE) {
                    oppTerritory += (10 - oppdistance);
                }
            }
                
        }

        // Combine factors — territory is weighted higher because it determines
        // long-term winning chances, while mobility is a more immediate indicator
            int myFreedom = queenFreedom(state, myPlayer);
            int oppFreedom = queenFreedom(state, opponent);

            int myCenter = centerControl(state, myPlayer);
            int oppCenter = centerControl(state, opponent);

            int myTrapped = trappedQueens(state, myPlayer);
            int oppTrapped = trappedQueens(state, opponent);

            int globalScore = 4 * (myTerritory - oppTerritory)
                 + 2 * (myFreedom - oppFreedom)
                 + 1 * (myCenter - oppCenter)
                 - 12 * (myTrapped - oppTrapped);
           int roleScore = computeRoleScore(state, myPlayer, opponent, myDist, oppDist);
           return globalScore + roleScore;

        }
    private int countMobility(int[][] state, int player) {
        boolean[][] visited = new boolean[BOARD_SIZE][BOARD_SIZE];
        int moves = 0;

        for (int r = 1; r < BOARD_SIZE; r++) {
            for (int c = 1; c < BOARD_SIZE; c++) {
                if (state[r][c] == player) {
                	Queue<int[]> queue = new LinkedList<>();
                	queue.add(new int[]{r,c});
                	while (!queue.isEmpty()) {
                        int[] cur = queue.poll();
                        int cr = cur[0], cc = cur[1];

                        if (!insideBoard(cr, cc)) continue;
                        if (visited[cr][cc]) continue;
                        if (state[cr][cc] != EMPTY && state[cr][cc] != player) continue;

                        visited[cr][cc] = true;

                        if (state[cr][cc] == EMPTY) moves++;

                        for (int[] d : DIRECTIONS) {
                            int nr = cr + d[0];
                            int nc = cc + d[1];
                            if (insideBoard(nr, nc) && !visited[nr][nc]
                                    && (state[nr][nc] == EMPTY || state[nr][nc] == player)) {
                                queue.add(new int[]{nr, nc});
                            }
                        }
                    }
                }
            }
        }

        return moves;
    }
    private int computeRoleScore(int[][] state, int myPlayer, int opponent,
            int[][] myDist, int[][] oppDist) {
			int score = 0;
			
			// Score our own queens
			for (int r = 1; r < BOARD_SIZE; r++) {
				for (int c = 1; c < BOARD_SIZE; c++) {
					if (state[r][c] != myPlayer) continue;
			
			QueenRole role = assignRole(state, r, c, myDist, oppDist);
			
				switch (role) {
				case AGGRESSIVE:
				  score += aggressiveBonus(state, r, c, oppDist);
				  break;
				case DEFENSIVE:
				  score += defensiveBonus(state, r, c);
				  break;
				case NEUTRAL:
				  // no adjustment
				  break;
					}
				}
			}
			
			// Score opponent queens — defensive opponents are good for us
		for (int r = 1; r < BOARD_SIZE; r++) {
			for (int c = 1; c < BOARD_SIZE; c++) {
				if (state[r][c] != opponent) continue;
		
		QueenRole oppRole = assignRole(state, r, c, oppDist, myDist);
		
			if (oppRole == QueenRole.DEFENSIVE) {
			// Opponent queen is cornered — treat it as a bonus for us
			score += DEFENSIVE_STUCK_PENALTY;
				}
			}
		}
			
			return score;
		}
    private QueenRole assignRole(int[][] state, int r, int c,
            int[][] myDist, int[][] oppDist) {
			// Count reachable empty cells in one queen move
			int freedom = 0;
			for (int[] d : DIRECTIONS) {
				int nr = r + d[0];
				int nc = c + d[1];
				while (insideBoard(nr, nc) && state[nr][nc] == EMPTY) {
				freedom++;
				nr += d[0];
				nc += d[1];
				}
			}
			
			// Count cells within BFS distance 3 that the opponent can reach
			// A high number means opponent queens are close and threatening
			int pressure = 0;
			for (int pr = 1; pr < BOARD_SIZE; pr++) {
				for (int pc = 1; pc < BOARD_SIZE; pc++) {
					if (oppDist[pr][pc] != Integer.MAX_VALUE && oppDist[pr][pc] <= 3) {
						pressure++;
					}
				}
			}
			
			double ratio = (double) freedom / (pressure + 1);
			
			if (ratio > AGGRESSION_THRESHOLD) return QueenRole.AGGRESSIVE;
			if (ratio < DEFENSE_THRESHOLD)    return QueenRole.DEFENSIVE;
			return QueenRole.NEUTRAL;
			}
    
    private int aggressiveBonus(int[][] state, int queenRow, int queenCol, int[][] oppDist) {
        int bonus = 0;

        for (int[] d : DIRECTIONS) {
            int nr = queenRow + d[0];
            int nc = queenCol + d[1];

            while (insideBoard(nr, nc) && state[nr][nc] == EMPTY) {
                // Cell directly adjacent to an opponent queen → high-value arrow target
                if (oppDist[nr][nc] == 1) {
                    bonus += AGGRESSIVE_BONUS_PER_BLOCKED;
                }
                nr += d[0];
                nc += d[1];
            }
        }

        return bonus;
    }

    private int defensiveBonus(int[][] state, int queenRow, int queenCol) {
        int escapeRoutes = 0;

        for (int[] d : DIRECTIONS) {
            int nr = queenRow + d[0];
            int nc = queenCol + d[1];

            while (insideBoard(nr, nc) && state[nr][nc] == EMPTY) {
                escapeRoutes++;
                nr += d[0];
                nc += d[1];
            }
        }

        if (escapeRoutes <= 2) {
            // Nearly trapped — penalize to encourage escaping
            return -DEFENSIVE_STUCK_PENALTY;
        }

        // Reward proportionally to how much open space exists
        return escapeRoutes * DEFENSIVE_BONUS_PER_ESCAPE;
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