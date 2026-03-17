package ubc.cosc322;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Minimax {

    private static final int MAX_DEPTH = 2;
    private static final int CENTER = 5;

    private static final int WIN_SCORE = 1_000_000;
    private static final int LOSS_SCORE = -1_000_000;

    private final AI_AmazonGame AI;
    private final int myPlayer;
    private final int opponent;

    public Minimax(AI_AmazonGame AI, int player) {
        this.AI = AI;
        this.myPlayer = player;
        this.opponent = (player == AI_AmazonGame.WHITE)
                ? AI_AmazonGame.BLACK
                : AI_AmazonGame.WHITE;
    }

    public AI_AmazonGame.Move selectBest() {
        List<AI_AmazonGame.Move> moves = AI.generateMoves(AI.getBoard(), myPlayer);

        if (moves.isEmpty()) {
            System.out.println("No legal moves — game over for me.");
            return null;
        }

        orderMoves(moves, AI.getBoard(), myPlayer);

        AI_AmazonGame.Move bestMove = null;
        int bestScore = LOSS_SCORE;

        if (moves.size() > 40) {
            moves = moves.subList(0, 40);
        }

        for (AI_AmazonGame.Move m : moves) {
            int[][] tempBoard = AI.copyBoard(AI.getBoard());
            AI.applyMove(tempBoard, m, myPlayer);

            int value = minimax(tempBoard, MAX_DEPTH - 1, false, LOSS_SCORE, WIN_SCORE);

            if (value > bestScore || (value == bestScore && isBetterTiebreak(m, bestMove))) {
                bestScore = value;
                bestMove = m;
            }
        }

        System.out.println("Best move score: " + bestScore);
        return bestMove;
    }

    private boolean isBetterTiebreak(AI_AmazonGame.Move candidate, AI_AmazonGame.Move current) {
        if (current == null) return true;

        int distCandidate = Math.abs(candidate.queenFuture[0] - CENTER)
                          + Math.abs(candidate.queenFuture[1] - CENTER);

        int distCurrent = Math.abs(current.queenFuture[0] - CENTER)
                        + Math.abs(current.queenFuture[1] - CENTER);

        return distCandidate < distCurrent;
    }

    private int minimax(int[][] state, int depth, boolean maximizing, int alpha, int beta) {
        int currentPlayer = maximizing ? myPlayer : opponent;

        if (depth == 0) {
            return AI.evaluateBoard(state, myPlayer);
        }

        List<AI_AmazonGame.Move> moves = AI.generateMoves(state, currentPlayer);

        if (moves.isEmpty()) {
            if (currentPlayer == myPlayer) {
                return LOSS_SCORE + depth;
            } else {
                return WIN_SCORE - depth;
            }
        }

        orderMoves(moves, state, currentPlayer);

        if (maximizing) {
            int maxEval = LOSS_SCORE;

            for (AI_AmazonGame.Move m : moves) {
                int[][] temp = AI.copyBoard(state);
                AI.applyMove(temp, m, myPlayer);

                int eval = minimax(temp, depth - 1, false, alpha, beta);

                if (eval > maxEval) {
                    maxEval = eval;
                }

                if (eval > alpha) {
                    alpha = eval;
                }

                if (beta <= alpha) {
                    break;
                }
            }

            return maxEval;
        } else {
            int minEval = WIN_SCORE;

            for (AI_AmazonGame.Move m : moves) {
                int[][] temp = AI.copyBoard(state);
                AI.applyMove(temp, m, opponent);

                int eval = minimax(temp, depth - 1, true, alpha, beta);

                if (eval < minEval) {
                    minEval = eval;
                }

                if (eval < beta) {
                    beta = eval;
                }

                if (beta <= alpha) {
                    break;
                }
            }

            return minEval;
        }
    }

    private void orderMoves(List<AI_AmazonGame.Move> moves, int[][] state, int player) {
        boolean isMaximizing = (player == myPlayer);

        Map<AI_AmazonGame.Move, Integer> scores = new HashMap<>();

        for (AI_AmazonGame.Move m : moves) {
            int[][] temp = AI.copyBoard(state);
            AI.applyMove(temp, m, player);
            int score = AI.evaluateBoard(temp, myPlayer);
            scores.put(m, score);
        }

        moves.sort((a, b) -> {
            int scoreA = scores.get(a);
            int scoreB = scores.get(b);

            if (isMaximizing) {
                return Integer.compare(scoreB, scoreA);
            } else {
                return Integer.compare(scoreA, scoreB);
            }
        });
    }
}