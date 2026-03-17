package ubc.cosc322;

import java.util.ArrayList;
import java.util.HashMap;
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

    private int myPlayer = 0;

    
  
    private static final int STARTING_PLAYER = AI_AmazonGame.BLACK;

    private AI_AmazonGame.Move lastSentMove = null;

    public static void main(String[] args) {
        String userName = (args.length > 0) ? args[0] : "player1";
        String passwd = (args.length > 1) ? args[1] : "pass";

        System.out.println("Starting client with username = " + userName);

        COSC322Test player = new COSC322Test(userName, passwd);

        BaseGameGUI.sys_setup();
        java.awt.EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                player.Go();
            }
        });
    }

    public COSC322Test(String userName, String passwd) {
        this.userName = userName;
        this.passwd = passwd;

        this.AI = new AI_AmazonGame();
        this.gamegui = new BaseGameGUI(this);

        System.out.println("COSC322Test created for user: " + userName);
    }

    @Override
    public void onLogin() {
        userName = gameClient.getUserName();
        System.out.println("Logged in successfully as: " + userName);

        if (gamegui != null) {
            gamegui.setRoomInformation(gameClient.getRoomList());
        }
    }

    @Override
    public boolean handleGameMessage(String messageType, Map<String, Object> msgDetails) {
        System.out.println("\n========== GAME MESSAGE RECEIVED ==========");
        System.out.println("Type: " + messageType);
        System.out.println("Details: " + msgDetails);

        if (messageType.equals(GameMessage.GAME_ACTION_START)) {
            String white = (String) msgDetails.get("player-white");
            String black = (String) msgDetails.get("player-black");

            System.out.println("Logged in as: " + userName);
            System.out.println("White player: " + white);
            System.out.println("Black player: " + black);

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

            if (myPlayer == STARTING_PLAYER) {
                System.out.println("Making opening move as "
                        + (myPlayer == AI_AmazonGame.WHITE ? "WHITE" : "BLACK") + "...");
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
                System.out.println("ERROR: myPlayer is still 0.");
                return true;
            }

            ArrayList<Integer> queenCurr =
                (ArrayList<Integer>) msgDetails.get(AmazonsGameMessage.QUEEN_POS_CURR);
            ArrayList<Integer> queenNext =
                (ArrayList<Integer>) msgDetails.get(AmazonsGameMessage.QUEEN_POS_NEXT);
            ArrayList<Integer> arrowPos =
                (ArrayList<Integer>) msgDetails.get(AmazonsGameMessage.ARROW_POS);

            if (queenCurr == null || queenNext == null || arrowPos == null) {
                System.out.println("ERROR: Move message missing one or more components.");
                return true;
            }

            AI_AmazonGame.Move incomingMove = new AI_AmazonGame.Move(
                new int[]{queenCurr.get(0), queenCurr.get(1)},
                new int[]{queenNext.get(0), queenNext.get(1)},
                new int[]{arrowPos.get(0), arrowPos.get(1)}
            );

            System.out.println("Incoming move: " + incomingMove);

            int opponent = (myPlayer == AI_AmazonGame.WHITE)
                    ? AI_AmazonGame.BLACK
                    : AI_AmazonGame.WHITE;

            // Ignore my own echoed move if the server sends it back
            if (sameMove(incomingMove, lastSentMove)) {
                System.out.println("Received my own echoed move from server. Ignoring.");
                lastSentMove = null;
                return true;
            }

            AI.applyMove(AI.getBoard(), incomingMove, opponent);

            if (gamegui != null) {
                gamegui.updateGameState(msgDetails);
            }

            System.out.println("Opponent moved: " + incomingMove);
            System.out.println("Computing my response...");
            sendBestMove();
        }

        return true;
    }

    private void sendBestMove() {
        if (minimaxAI == null) {
            System.out.println("ERROR: minimaxAI is null.");
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

        AI.applyMove(AI.getBoard(), bestMove, myPlayer);

        lastSentMove = bestMove;

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

    private boolean sameMove(AI_AmazonGame.Move a, AI_AmazonGame.Move b) {
        if (a == null || b == null) return false;

        return a.queenPast[0] == b.queenPast[0]
            && a.queenPast[1] == b.queenPast[1]
            && a.queenFuture[0] == b.queenFuture[0]
            && a.queenFuture[1] == b.queenFuture[1]
            && a.arrow[0] == b.arrow[0]
            && a.arrow[1] == b.arrow[1];
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
        System.out.println("Connecting to game server as " + userName + "...");
        gameClient = new GameClient(userName, passwd, this);
    }
}