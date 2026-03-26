package ubc.cosc322;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class Minimax {

    // How many half-turns (plies) ahead the search looks
    // Depth 2 = we move, opponent responds — a good balance of strength vs speed
    private static final int MAX_DEPTH = 2;

    // Center of the 10x10 board — used to break ties by preferring central moves
    private static final int CENTER = 5;

    // Safe terminal scores — large enough to dominate any evaluation score,
    // but not Integer.MAX/MIN_VALUE which can overflow when arithmetic is applied
    private static final int WIN_SCORE  =  1_000_000;
    private static final int LOSS_SCORE = -1_000_000;

    // Reference to the game engine — used for move generation, board copy, and evaluation
    private final AI_AmazonGame AI;

    // Our player number (WHITE=1 or BLACK=2)
    private final int myPlayer;

    // Opponent's player number — pre-computed once to avoid repeating the ternary everywhere
    private final int opponent;
   // private int moveCount = 0;

    
    /**
     * Builds the Minimax engine for the given player.
     * Must be called AFTER we know our color (i.e., after GAME_ACTION_START),
     * otherwise myPlayer would be 0 and move generation would produce no moves.
     */
    public Minimax(AI_AmazonGame AI, int player) {
        this.AI       = AI;
        this.myPlayer = player;
        // Derive opponent once — used in every recursive call
        this.opponent = (player == AI_AmazonGame.WHITE)
                ? AI_AmazonGame.BLACK
                : AI_AmazonGame.WHITE;
    }

    public AI_AmazonGame.Move selectBest() {

        // Get all moves we can legally make from the current board position
        List<AI_AmazonGame.Move> moves = AI.generateMoves(AI.getBoard(), myPlayer);

        // Empty list means all our queens are blocked — no legal move possible
        if (moves.isEmpty()) {
            System.out.println("No legal moves — game over for me.");
            return null;
        }
     // Opening, only for BLACK on the very first move
        //Not yet implemented but good idea
       // if (myPlayer == AI_AmazonGame.BLACK && isOpeningMove()) {
         //   AI_AmazonGame.Move opening = getOpeningMove();
           ////   System.out.println("Using opening move");
               // moveCount++;
                //return opening;
            //}
        //}
 
        //moveCount++;

        // Sort moves so the most promising ones are evaluated first
        // This maximizes alpha-beta pruning effectiveness at deeper levels
        orderMoves(moves, AI.getBoard(), myPlayer);
        AI_AmazonGame.Move bestMove = null;
        int bestScore = LOSS_SCORE;
        // In the opening there can be hundreds of moves — trim to top 40
        // after ordering so we keep the best candidates
        // This prevents timeout while still exploring good options
        if (moves.size() > 40) {
            moves = moves.subList(0, 40);
        }

        // Evaluate each candidate move by simulating it and running Minimax
        for (AI_AmazonGame.Move m : moves) {

            // Work on a copy — never modify the real board during search
            int[][] tempBoard = AI.copyBoard(AI.getBoard());
            AI.applyMove(tempBoard, m, myPlayer);

            // Search MAX_DEPTH-1 more levels after our move 
            int value = minimax(tempBoard, MAX_DEPTH - 1, false, LOSS_SCORE, WIN_SCORE);

            // Keep the move that produces the highest score
            // a tie prefer the move that puts the queen closer to the center
            if (value > bestScore || (value == bestScore && isBetterTiebreak(m, bestMove))) {
                bestScore = value;
                bestMove  = m;
            }
        }

        System.out.println("Best move score: " + bestScore);
        return bestMove;
    }
    
    //Not yet implemented
    private boolean isOpeningMove() {
        int[][] board = AI.getBoard();
        for (int r = 1; r <= 10; r++)
            for (int c = 1; c <= 10; c++)
                if (board[r][c] == AI_AmazonGame.ARROW) return false;
        return true;
    }
    //I searh some ideas of best movement if we are blacks 
    //not sure if the best not yet implemented
    private AI_AmazonGame.Move getOpeningMove() {
        int[][] board = AI.getBoard();
 
        int[][] openings = {{10, 4, 7, 4, 4, 4},   
                {10, 7, 7, 7, 4, 7},   
                {7, 10, 7, 7, 7, 4},   
                {4, 10, 4, 7, 4, 4},};

 
            for(int[] opening: openings) {
            if (board[opening[0]][opening[1]] == AI_AmazonGame.BLACK) {
                System.out.println("Opening: queen confirmed at ("
                        + opening[0] + "," + opening[1] + ") -> (" + opening[2] + "," + opening[3]
                        + ") arrow (" + opening[4] + "," + opening[5] + ")");
                return new AI_AmazonGame.Move(
                    new int[]{opening[0], opening[1]},
                    new int[]{opening[2],  opening[3]},
                    new int[]{opening[4],  opening[5]}
                );
            }
        }
 
        return null; 
    }
    private boolean isBetterTiebreak(AI_AmazonGame.Move candidate, AI_AmazonGame.Move current) {

        // First call always wins (nothing to compare against yet)
        if (current == null) return true;

        // Distance from queen's destination to the center for each move
        int distCandidate = Math.abs(candidate.queenFuture[0] - CENTER)
                          + Math.abs(candidate.queenFuture[1] - CENTER);

        int distCurrent   = Math.abs(current.queenFuture[0] - CENTER)
                          + Math.abs(current.queenFuture[1] - CENTER);

        // Smaller distance = closer to center = better
        return distCandidate < distCurrent;
    }

    
    private int minimax(int[][] state, int depth, boolean maximizing, int alpha, int beta) {

        // Who is moving at this level of the tree
        int currentPlayer = maximizing ? myPlayer : opponent;

        // Base case: depth limit reached — score the position as-is
        if (depth == 0) {
            return AI.evaluateBoard(state, myPlayer);
        }

        // Generate all legal moves for the current player at this board state
        List<AI_AmazonGame.Move> moves = AI.generateMoves(state, currentPlayer);

        // Terminal state current player has no move they lose
        if (moves.isEmpty()) {
            if (currentPlayer == myPlayer) {
                return LOSS_SCORE + depth;//depth we want to win fast
            } else {
                return WIN_SCORE - depth;//Depth we want to loss slow
            }
        }

        // Order moves best-first to maximize the number of alpha-beta cut-offs
        orderMoves(moves, state, currentPlayer);

        if (maximizing) {
            // our turn, find the move with the highest score
            int maxEval = LOSS_SCORE;

            for (AI_AmazonGame.Move m : moves) {
                // Simulate this move on a copy,
            	//never touch the original
                int[][] temp = AI.copyBoard(state);
                AI.applyMove(temp, m, myPlayer);

                // recurse opponent responds next (
                int eval = minimax(temp, depth - 1, false, alpha, beta);

                if (eval > maxEval) {
                    maxEval = eval;
                }

                // Raise the floor  we now know we can get at least eval
                if (eval > alpha) {
                    alpha = eval;
                }

                // Beta cut-off opponent already has a path that is better for them
                // than anything we can get here stop exploring this branch
                if (beta <= alpha) {
                    break;
                }
            }

            return maxEval;

        } else {
            // Opponent's turn find the move with the lowest score
            int minEval = WIN_SCORE;

            for (AI_AmazonGame.Move m : moves) {
                // Simulate this move on a copy
                int[][] temp = AI.copyBoard(state);
                AI.applyMove(temp, m, opponent);

                // Recurse: our turn next (maximizing)
                int eval = minimax(temp, depth - 1, true, alpha, beta);

                if (eval < minEval) {
                    minEval = eval;
                }

                // Lower the ceiling — opponent can now guarantee at most eval
                if (eval < beta) {
                    beta = eval;
                }

                // Alpha cut-off: we already have a path better than anything the opponent
                // can achieve here — stop exploring this branch
                if (beta <= alpha) {
                    break;
                }
            }

            return minEval;
        }
    }

    
    private void orderMoves(List<AI_AmazonGame.Move> moves, int[][] state, int player) {

        boolean isMaximizing = (player == myPlayer);

        // Pre-compute scores for all moves 
        // Without the HashMap, the sort comparator would recompute scores on every comparison
        Map<AI_AmazonGame.Move, Integer> scores = new HashMap<>();
        for (AI_AmazonGame.Move m : moves) {
            int[][] temp = AI.copyBoard(state);
            AI.applyMove(temp, m, player);
            // Always evaluate from our perspective (myPlayer) so scores are comparable
            
            scores.put(m, AI.evaluateBoard(temp, myPlayer));
        }

        // Sort using the cached scores — Integer.compare is overflow-safe
        moves.sort((a, b) -> {
            int scoreA = scores.get(a);
            int scoreB = scores.get(b);

            if (isMaximizing) {
                return Integer.compare(scoreB, scoreA); // higher scores first
            } else {
                return Integer.compare(scoreA, scoreB); // lower scores first
            }
        });
    }
}