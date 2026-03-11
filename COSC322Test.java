package ubc.cosc322;

import java.util.ArrayList;
import java.util.HashMap; // ADDED: needed to update GUI for our own move
import java.util.Map;

import ygraph.ai.smartfox.games.BaseGameGUI;
import ygraph.ai.smartfox.games.GameClient;
import ygraph.ai.smartfox.games.GameMessage;
import ygraph.ai.smartfox.games.GamePlayer;
import ygraph.ai.smartfox.games.amazons.AmazonsGameMessage;

public class COSC322Test extends GamePlayer {

    private GameClient gameClient = null;
    private BaseGameGUI gamegui = null;

    private String userName = null;
    private String passwd = null;

    private AI_AmazonGame AI;
    private Minimax minimaxAI;

    // 1 = WHITE, 2 = BLACK
    private int myPlayer = 0;

    /**
     * Main method
     */
    public static void main(String[] args) {
        // CHANGED: safe fallback arguments so Eclipse won't crash if args are missing
        String userName = (args.length > 0) ? args[0] : "player1";
        String passwd   = (args.length > 1) ? args[1] : "pass";

        System.out.println("Starting client with username = " + userName); // DEBUG

        COSC322Test player = new COSC322Test(userName, passwd);

        BaseGameGUI.sys_setup();
        java.awt.EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                player.Go();
            }
        });
    }

    /**
     * Constructor
     */
    public COSC322Test(String userName, String passwd) {
        this.userName = userName;
        this.passwd = passwd;

        this.AI = new AI_AmazonGame();
        this.gamegui = new BaseGameGUI(this);

        System.out.println("COSC322Test created for user: " + userName); // DEBUG
    }

    @Override
    public void onLogin() {
        userName = gameClient.getUserName();
        System.out.println("Logged in successfully as: " + userName); // DEBUG

        if (gamegui != null) {
            gamegui.setRoomInformation(gameClient.getRoomList());
        }
    }

    @Override
    public boolean handleGameMessage(String messageType, Map<String, Object> msgDetails) {
        System.out.println("\n========== GAME MESSAGE RECEIVED =========="); // DEBUG
        System.out.println("Type: " + messageType); // DEBUG
        System.out.println("Details: " + msgDetails); // DEBUG

        if (messageType.equals(GameMessage.GAME_ACTION_START)) {
            String white = (String) msgDetails.get("player-white");
            String black = (String) msgDetails.get("player-black");

            System.out.println("Logged in as: " + userName); // DEBUG
            System.out.println("White player: " + white);   // DEBUG
            System.out.println("Black player: " + black);   // DEBUG

            if (userName != null && userName.equals(white)) {
                myPlayer = AI_AmazonGame.WHITE;
                System.out.println("I am WHITE (player 1)");
            } else if (userName != null && userName.equals(black)) {
                myPlayer = AI_AmazonGame.BLACK;
                System.out.println("I am BLACK (player 2)");
            } else {
                myPlayer = 0;
                System.out.println("ERROR: My username does not match either WHITE or BLACK.");
                return true;
            }

            minimaxAI = new Minimax(AI, myPlayer);

            // Black starts first
            if (myPlayer == AI_AmazonGame.BLACK) {
                System.out.println("Making opening move as WHITE...");
                sendBestMove();
            }
        }

        else if (messageType.equals(GameMessage.GAME_STATE_BOARD)) {
            ArrayList<Integer> gameState = (ArrayList<Integer>) msgDetails.get("game-state");

            if (gameState == null) {
                System.out.println("ERROR: game-state is null.");
                return true;
            }

            System.out.println("Received initial board state.");
            AI.updateBoard(gameState);

            if (gamegui != null) {
                gamegui.setGameState(gameState);
            }
        }

        else if (messageType.equals(GameMessage.GAME_ACTION_MOVE)) {
            if (myPlayer == 0) {
                System.out.println("ERROR: myPlayer is still 0, cannot process opponent move properly.");
                return true;
            }

            ArrayList<Integer> queenCurr =
                (ArrayList<Integer>) msgDetails.get(AmazonsGameMessage.QUEEN_POS_CURR);
            ArrayList<Integer> queenNext =
                (ArrayList<Integer>) msgDetails.get(AmazonsGameMessage.QUEEN_POS_NEXT);
            ArrayList<Integer> arrowPos =
                (ArrayList<Integer>) msgDetails.get(AmazonsGameMessage.ARROW_POS);

            if (queenCurr == null || queenNext == null || arrowPos == null) {
                System.out.println("ERROR: Move message missing one or more move components.");
                return true;
            }

            System.out.println("QCurr: " + queenCurr); // DEBUG
            System.out.println("QNew: " + queenNext); // DEBUG
            System.out.println("Arrow: " + arrowPos); // DEBUG

            int opponent = (myPlayer == AI_AmazonGame.WHITE)
                    ? AI_AmazonGame.BLACK
                    : AI_AmazonGame.WHITE;

            AI_AmazonGame.Move opponentMove = new AI_AmazonGame.Move(
                new int[]{queenCurr.get(0), queenCurr.get(1)},
                new int[]{queenNext.get(0), queenNext.get(1)},
                new int[]{arrowPos.get(0), arrowPos.get(1)}
            );

            // CHANGED: safer because AI.applyMove now validates legality
            AI.applyMove(AI.getBoard(), opponentMove, opponent);

            if (gamegui != null) {
                gamegui.updateGameState(msgDetails);
            }

            System.out.println("Opponent moved: " + opponentMove);
            System.out.println("Computing my response...");
            sendBestMove();
        }

        return true;
    }

    /**
     * Compute and send the best move
     */
    private void sendBestMove() {
        if (minimaxAI == null) {
            System.out.println("ERROR: minimaxAI is null. Cannot send move.");
            return;
        }

        AI_AmazonGame.Move bestMove = minimaxAI.selectBest();

        if (bestMove == null) {
            System.out.println("No legal moves — game over for me.");
            System.out.println("No moves available — I lose.");
            return;
        }

        ArrayList<Integer> queenFrom = new ArrayList<>();
        queenFrom.add(bestMove.queenPast[0]);
        queenFrom.add(bestMove.queenPast[1]);

        ArrayList<Integer> queenTo = new ArrayList<>();
        queenTo.add(bestMove.queenFuture[0]);
        queenTo.add(bestMove.queenFuture[1]);

        ArrayList<Integer> arrow = new ArrayList<>();
        arrow.add(bestMove.arrow[0]);
        arrow.add(bestMove.arrow[1]);

        // CHANGED: update internal board first
        AI.applyMove(AI.getBoard(), bestMove, myPlayer);

        // ADDED: update our own GUI too, otherwise board visuals desync
        if (gamegui != null) {
            Map<String, Object> myMove = new HashMap<>();
            myMove.put(AmazonsGameMessage.QUEEN_POS_CURR, queenFrom);
            myMove.put(AmazonsGameMessage.QUEEN_POS_NEXT, queenTo);
            myMove.put(AmazonsGameMessage.ARROW_POS, arrow);
            gamegui.updateGameState(myMove);
        }

        System.out.println("Sending move:");
        System.out.println("  Queen from: " + queenFrom);
        System.out.println("  Queen to:   " + queenTo);
        System.out.println("  Arrow to:   " + arrow);

        if (gameClient != null) {
            gameClient.sendMoveMessage(queenFrom, queenTo, arrow);
        } else {
            System.out.println("ERROR: gameClient is null. Move not sent.");
        }
    }

    @Override
    public String userName() {
        return userName;
    }

    @Override
    public GameClient getGameClient() {
        return this.gameClient;
    }

    @Override
    public BaseGameGUI getGameGUI() {
        return this.gamegui;
    }

    @Override
    public void connect() {
        System.out.println("Connecting to game server as " + userName + "..."); // DEBUG
        gameClient = new GameClient(userName, passwd, this);
    }

}
