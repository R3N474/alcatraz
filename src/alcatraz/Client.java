/*
 * IT Security Narmandakh Chuluunbaatar "Client"
 */

package alcatraz;

import at.falb.games.alcatraz.api.Alcatraz;
import at.falb.games.alcatraz.api.MoveListener;
import at.falb.games.alcatraz.api.Player;
import at.falb.games.alcatraz.api.Prisoner;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.rmi.AlreadyBoundException;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Client extends UnicastRemoteObject implements IClient, MoveListener, Serializable {
    private static final long serialVersionUID = 1L;

    private static final ExecutorService THREAD_POOL = Executors.newCachedThreadPool();

    private Alcatraz game = null;

    private static WorkerThread workerThread = null;

    private List<IServer> servers = new LinkedList<IServer>();
    private static IClient otherClients[] = new IClient[3];

    private String playerName;
    private int playerCount = 0;
    private int tempPlayerNumber = 0;

    private int nextMovePlayerId = 0;
    private int nextMovePrisonerId = 0;
    private int nextMoveRowOrCol = 0;
    private int nextMoveRow = 0;
    private int nextMoveCol = 0;

    private static boolean signedOut = false;

    private boolean gameStarted = false;
    private boolean receivedMessage = false;
    private int receiver = 0;
    private static int counter = 0;

    private static int twoPhaseCommit = 0;    // 1 = Vote-Request, 2 = Vote-Commit, 3 = Global-Commit, 4 = Ack

    public Client() throws RemoteException {

    }

    public void gameWon(Player player) {
        System.out.println("Player " + player.getId() + " wins.");
    }

    public void undoMove() {
        System.out.println("Undoing move");
    }
    
    /* Read name from console */

    public void setName(String name) {
        playerName = name;
    }

    public String getName() throws RemoteException {
        return playerName;
    }

    public void saveServerRef(IServer serverRef) throws RemoteException {
        servers.add(serverRef);
    }

    /**
     * Each client gets all client-references and number of players and saves this information
     */
    public void deliverReference(IClient[] refList, int playerCount) throws RemoteException {
        game = new Alcatraz();

        /* detect own ID between 0 to 3 */
        int j = 0;
        while (!refList[j].getName().equals(playerName)) j++;
        int myID = j;
        this.playerCount = playerCount;
        game.init(this.playerCount, myID);

        int k = 0;
        for (int i = 0; i < this.playerCount; i++) {
            game.getPlayer(i).setName(refList[i].getName());

            if (i != myID) {
                otherClients[k] = refList[i];
                k++;
            }
        }

        game.showWindow();
        game.addMoveListener(this); // A MoveListener (Client) is added by the game Alcatraz 
        game.start();
    }

    public void startGame() {
        for(IServer server : servers) {
            try {
                server.startAlcatraz(this);
                gameStarted = true;
                System.out.printf("%s has successfully started the GAME.%n", getName());
                return;
            } catch (RemoteException e) {
                // ignore and try next
            } catch (Exception e) {
                System.err.println("Error starting game: " + e.getMessage());
                return;
            }
        }
        
        if (!gameStarted) {
            System.out.println("No server available to start.");
        }
    }

    public void registerWithServer(IServer server) throws Exception {
        server.register(this);
    }

    public void signOut() {
        for (IServer server : servers) {
            try {
                server.signOut(this);
                signedOut = true;
                System.out.println("successfully signed out.");
                return;              
            } catch(RemoteException e) {
                // ignore and try next server
            }
        }

        System.out.println("No server available to sign out.");
    }

    public void signIn() {
        for (IServer server : servers) {
            try {
                server.register(this);
                signedOut = false;
                System.out.println("successfully signed in.");
                return;
            } catch (RemoteException e) {
                // ignore and try next
            } catch (Exception e) {
                System.out.println("Error registering with the server: " + e.getMessage());
            }
        }
    }
       
    /* 
     *  Methods of Movelistener. Game board informs the client, that a move is done.
     *  A player moved -> Alcatraz-object (a1) calls the moveDone method of client-object (c1)
     *  client-object (c1) calls doMove method of other client-objects (c2, c3 and c4) to say which move c1 done
     */

    public void moveDone(Player player, Prisoner prisoner, int rowOrCol, int row, int col) {
        twoPhaseCommit = 1;
        tempPlayerNumber = playerCount - 1;

        System.out.printf("moving %s to %s %d%n", prisoner, rowOrCol == Alcatraz.ROW ? "row" : "col", rowOrCol == Alcatraz.ROW ? row : col);

        workerThread = new WorkerThread(player.getId(), prisoner.getId(), rowOrCol, row, col);    // create new thread

        // TODO: Thread.run() doesn't actually run a separate thread
        workerThread.run();
    }     
    
    /*
     * An other client (c1) calls the doMove method of this client (c2) to say which move the client (c1) done
     * Client (c2) calls doMove method of its Alcatraz (a2).
     */

    public void doMove(int playerId, int prisonerId, int rowOrCol, int row, int col, IClient ref) throws RemoteException {
        System.out.println(getName() + " received the doMove Message from " + ref.getName());

        twoPhaseCommit = 2;

        nextMovePlayerId = playerId;
        nextMovePrisonerId = prisonerId;
        nextMoveRowOrCol = rowOrCol;
        nextMoveRow = row;
        nextMoveCol = col;

        try {
            System.out.printf("%s sends voteCommit to %s%n", getName(), ref.getName());
            ref.receiveVoteCommit(this);
        } catch (RemoteException e) {
            System.err.println("Error sending voteCommit: " + e.getMessage());
        }

    }

    public void receiveVoteCommit(IClient ref) throws RemoteException {
        System.out.println(getName() + " got the voteCommit message from " + ref.getName());

        if (tempPlayerNumber > 0) {
            for (int i = 0; i < playerCount - 1; i++) {
                if (otherClients[i].equals(ref)) {
                    tempPlayerNumber--;
                    receivedMessage = true;
                    receiver = i;
                }
            }
        }

        if (tempPlayerNumber == 0) {
            System.out.println(getName() + " got all voteCommit messages from clients ");
            tempPlayerNumber = playerCount - 1;
            twoPhaseCommit = 3;
            workerThread.run();
        }
    }

    public void sendGlobalCommit(IClient ref) throws RemoteException {
        System.out.println(getName() + " got the globalCommit message from " + ref.getName());
        game.doMove(game.getPlayer(nextMovePlayerId), game.getPrisoner(nextMovePrisonerId), nextMoveRowOrCol, nextMoveRow, nextMoveCol);
        System.out.println(getName() + " sends an ACK to " + ref.getName());
        twoPhaseCommit = 4;
        ref.receiveACK(this);
    }

    public void receiveACK(IClient ref) throws RemoteException {
        System.out.println(getName() + " got the ACK message from " + ref.getName());

        for (int i = 0; i < playerCount - 1; i++) {
            if (otherClients[i].equals(ref)) {
                tempPlayerNumber--;
                receivedMessage = true;
                receiver = i;
            }
        }

        if (tempPlayerNumber == 0) {
            System.out.println(getName() + " got all ACK from clients.");
        }
    }    
    
    /* A thread */

    class WorkerThread implements Runnable {
        WorkerThread(int playerId, int prisonerId, int rowOrCol, int row, int col) {
            nextMovePlayerId = playerId;
            nextMovePrisonerId = prisonerId;
            nextMoveRowOrCol = rowOrCol;
            nextMoveRow = row;
            nextMoveCol = col;
        }

        @Override
        public void run() {
            for (int i = 0; i < Client.this.playerCount - 1; i++) {
                while (!receivedMessage || receiver != i) {
                    if (twoPhaseCommit == 1) {
                        try {
                            System.out.printf("%s sends doMove message to %s%n", Client.this.getName(), otherClients[i].getName());
                            otherClients[i].doMove(nextMovePlayerId, nextMovePrisonerId, nextMoveRowOrCol, nextMoveRow, nextMoveCol, Client.this);
                        } catch (RemoteException e) {
                            System.err.println("Error sending doMove message: " + e.getMessage());
                        }
                    }

                    if (twoPhaseCommit == 3) {
                        try {
                            System.out.printf("%s is sending global commit message to all clients%n", Client.this.getName());
                            otherClients[i].sendGlobalCommit(Client.this);
                        } catch (RemoteException e) {
                            System.err.println("Error sending global commit: " + e.getMessage());
                        }
                    }

                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        // ignore
                    }
                }

                receivedMessage = false;
                receiver = 0;
            }
        }
    }

    public static void main(String[] args) throws RemoteException, AlreadyBoundException {
        Client client = new Client();
        BufferedReader console = new BufferedReader(new InputStreamReader(System.in));

        // Client looks up the reference of the AlcatrazServer (service) and asks the RegisterServer. 
        // The IP-address, the port of RegisterServer and the name of AlcatrazServer are familiar.  args[0] = IPAddress

        IServer server = null;
        for (String serverToTry : args) {
            server = connectToServer(serverToTry);
            if (server != null) {
                break;
            }
        }
        if (server == null) {
            System.err.println("No server could be reached");
            System.exit(7);
        }
        System.out.println("Connected to server: " + server);

        String playerName;
        try {
            System.out.print("What is your name?: ");
            playerName = console.readLine();
            client.setName(playerName);
        } catch (IOException e) {
            System.err.println("Error reading input from stdin");
            System.exit(2);
        }

        // Client registers by the AlcatrazServer and sends its reference to AlcatrazServer

        try {
            client.registerWithServer(server);
        } catch (Exception e) {
            System.err.printf("Error registering the client with the server: %s%n", e.getMessage());
            System.exit(3);
        }

        showMenu(client, console);

        client.signOut();
    }

    private static void showMenu(Client client, BufferedReader console) {
        String playerName;
        boolean running = true;
        while (running) {
            System.out.println("Please enter\n\t1 for sign out and exit\n\t2 for start the game\n\t3 for choosing a new name or\n\t4 for sign out.\n");

            try {
                switch (Integer.valueOf(console.readLine())) {
                    case 1:
                        running = false;
                        client.signOut();
                        break;
                    case 2:
                        if (signedOut) {
                            System.err.println("Client is not registered.");
                        } else {
                            client.startGame();
                        }
                        break;
                    case 3:
                        client.signOut();

                        try {
                            System.out.print("What is your name?: ");
                            playerName = console.readLine();
                            client.setName(playerName);
                        } catch (IOException e) {
                            System.err.println("Error reading input from stdin");
                            System.exit(2);
                        }

                        client.signIn();
                        break;
                    case 4:
                        client.signOut();
                        break;
                    default:
                        System.err.println("Invalid option selected! (Choose number between 1-4)");

                }
            } catch (IOException e) {
                System.err.println("Error reading input from stdin");
                System.exit(2);
            }
        }
    }

    /**
     * Try to connect to the given registry server
     *
     * @param serverAddress IP address or DNS name of the RMI server
     * @return the server reference, or null if the connect could not be established
     */
    private static IServer connectToServer(String serverAddress) {
        String rmiUrl = "//" + serverAddress + ":1099/AlcatrazServer";
        try {
            System.out.printf("Trying to connect to server: %s%n", serverAddress);
            return (IServer) Naming.lookup(rmiUrl);
        } catch (MalformedURLException e) {
            System.err.printf("Invalid RMI URL: %s (%s)%n", rmiUrl, e.getMessage());
        } catch (Exception e) {
            System.err.printf("Error connecting to registry server %s: %s%n", serverAddress, e.getMessage());
        }
        return null;
    }
}
