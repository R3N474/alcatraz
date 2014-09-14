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

/**
 * A test class initializing a local Alcatraz game -- illustrating how
 * to use the Alcatraz API.
 */
public class Client extends UnicastRemoteObject implements IClient, MoveListener, Serializable {
    private static final long serialVersionUID = 1L;

    private Alcatraz alc = null;

    private static Client client;

    private static WorkerThread workerThread = null;

    private static IServer alcatrazServerRef = null;                // Client Stub
    private static IServer serverRefList[] = new IServer[10];
    private static IClient refOtherList[] = new IClient[3];
    private static IClient clientRefList[] = new IClient[4];

    private static String playerName;
    private static int option = 0;
    private int myID = 0, playerNumber = 0, tempPlayerNumber = 0;

    private static int nextMovePlayerId = 0;
    private static int nextMovePrisonerId = 0;
    private static int nextMoveRowOrCol = 0;
    private static int nextMoveRow = 0;
    private static int nextMoveCol = 0;

    private static boolean signedOut = false;

    private boolean startGame = false;
    private boolean receivedMessage = false;
    private int receiver = 0;
    private static int counter = 0;

    private static int twoPhaseCommit = 0;    // 1 = Vote-Request, 2 = Vote-Commit, 3 = Global-Commit, 4 = Ack

    public Client() throws RemoteException {

    }

    public int getNumPlayer() {
        return playerNumber;
    }

    public void setNumPlayer(int numPlayer) {
        this.playerNumber = numPlayer;
    }

    public void gameWon(Player player) {
        System.out.println("Player " + player.getId() + " wins.");
    }

    public void undoMove() {
        System.out.println("Undoing move");
    }
    
    /* Read name from console */

    public void setName() {
        BufferedReader console = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("What is your name?:\n");

        try {
            playerName = console.readLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getName() throws RemoteException {
        return playerName;
    }

    public void saveServerRef(IServer serverRef) throws RemoteException {
        serverRefList[counter] = serverRef;
        counter++;

        System.out.println(counter + ". Server");
    }

    /**
     * Each client gets all client-references and number of players and saves this information
     */
    public void deliverReference(IClient[] refList, int playerNumber) throws RemoteException {
        alc = new Alcatraz();

        clientRefList = refList;
        
    	/* detect own ID between 0 to 3 */

        int j = 0;
        while (!clientRefList[j].getName().equals(playerName)) j++;

        myID = j;

        client.setNumPlayer(playerNumber);

        alc.init(this.playerNumber, myID);

        int k = 0;
        for (int i = 0; i < this.playerNumber; i++) {
            alc.getPlayer(i).setName(clientRefList[i].getName());

            if (i != myID) {
                refOtherList[k] = clientRefList[i];
                k++;
            }
        }

        alc.showWindow();

        alc.addMoveListener(client); // A MoveListener (Client) is added by the game Alcatraz 

        alc.start();
    }

    public void startGame() {
        int i = 0;
        while (!startGame && i < counter) {
            try {
                serverRefList[i].startAlcatraz(client);
                startGame = true;
                System.out.printf("%s has successfully started the GAME.%n", client.getName());
            } catch (RemoteException e) {
                i++;
            } catch (Exception e) {
                System.err.println("Not enough player. Please wait.\n");
                return;
            }
        }

        if (!startGame) {
            System.out.println("No server available to start.");
        }
    }

    public void signOut() {
        int i = 0;
        while (signedOut == false && i < counter) {
            try {
                serverRefList[i].signOut(client);
                signedOut = true;
                System.out.println("successfully signed out.");
            } catch (RemoteException e) {
                i++;
            }
        }

        if (signedOut == false) {
            System.out.println("No server available to sign out.");
        }
    }

    public void signIn() {
        int i = 0;
        while (signedOut == true && i < counter) {
            try {
                serverRefList[i].register(client);
                signedOut = false;
                System.out.println("successfully signed in.");
            } catch (RemoteException e) {
                e.printStackTrace();
            } catch (Exception e) {
                System.out.println("The name exists already. Please give an another name.\n");
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
        tempPlayerNumber = getNumPlayer() - 1;

        System.out.println("moving " + prisoner + " to " + (rowOrCol == Alcatraz.ROW ? "row" : "col") + " " + (rowOrCol == Alcatraz.ROW ? row : col));

        workerThread = new WorkerThread(player.getId(), prisoner.getId(), rowOrCol, row, col);    // create new thread

        workerThread.run();
    }     
    
    /*
     * An other client (c1) calls the doMove method of this client (c2) to say which move the client (c1) done
     * Client (c2) calls doMove method of its Alcatraz (a2).
     */

    public void doMove(int playerId, int prisonerId, int rowOrCol, int row, int col, IClient ref) throws RemoteException {
        System.out.println(client.getName() + " becomes the doMove Message from " + ref.getName());

        twoPhaseCommit = 2;

        nextMovePlayerId = playerId;
        nextMovePrisonerId = prisonerId;
        nextMoveRowOrCol = rowOrCol;
        nextMoveRow = row;
        nextMoveCol = col;

        try {
            ref.receiveVoteCommit(client);
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        System.out.println(client.getName() + " sends voteCommit to " + ref.getName());
    }

    public void receiveVoteCommit(IClient ref) throws RemoteException {
        System.out.println(client.getName() + " got the voteCOmmit message from " + ref.getName());

        if (tempPlayerNumber > 0) {
            for (int i = 0; i < getNumPlayer() - 1; i++) {
                if (refOtherList[i].equals(ref)) {
                    tempPlayerNumber--;
                    receivedMessage = true;
                    receiver = i;
                }
            }
        }

        if (tempPlayerNumber == 0) {
            System.out.println(client.getName() + " got all voteCOmmit messages from clients ");

            tempPlayerNumber = getNumPlayer() - 1;

            twoPhaseCommit = 3;

            workerThread.run();
        }
    }

    public void sendGlobalCommit(IClient ref) throws RemoteException {
        System.out.println(client.getName() + " got the globalCOmmit message from " + ref.getName());

        alc.doMove(alc.getPlayer(nextMovePlayerId), alc.getPrisoner(nextMovePrisonerId), nextMoveRowOrCol, nextMoveRow, nextMoveCol);

        System.out.println(client.getName() + " sends an ACK to " + ref.getName());

        twoPhaseCommit = 4;

        ref.receiveACK(client);
    }

    public void receiveACK(IClient ref) throws RemoteException {
        System.out.println(client.getName() + " got the ACK message from " + ref.getName());

        for (int i = 0; i < getNumPlayer() - 1; i++) {
            if (refOtherList[i].equals(ref)) {
                tempPlayerNumber--;
                receivedMessage = true;
                receiver = i;
            }
        }

        if (tempPlayerNumber == 0) {
            System.out.println(client.getName() + " got all ACK from clients.");
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
            for (int i = 0; i < client.getNumPlayer() - 1; i++) {
                while (!receivedMessage || receiver != i) {
                    if (twoPhaseCommit == 1) {
                        try {
                            System.out.printf("%s sends doMove message to %s%n", client.getName(), refOtherList[i].getName());
                            refOtherList[i].doMove(nextMovePlayerId, nextMovePrisonerId, nextMoveRowOrCol, nextMoveRow, nextMoveCol, client);
                        } catch (RemoteException e) {
                            System.err.println("Error sending doMove message: " + e.getMessage());
                            // TODO: Should we terminate the program here?
                        }
                    }

                    if (twoPhaseCommit == 3) {
                        try {
                            System.out.printf("%s is sending global commit message to all clients%n", client.getName());
                            refOtherList[i].sendGlobalCommit(client);
                        } catch (RemoteException e) {
                            System.err.println("Error sending global commit: " + e.getMessage());
                            // TODO: Should we terminate the program here?
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
        client = new Client();
        BufferedReader console = new BufferedReader(new InputStreamReader(System.in));

        // Client looks up the reference of the AlcatrazServer (service) and asks the RegisterServer. 
        // The IP-address, the port of RegisterServer and the name of AlcatrazServer are familiar.  args[0] = IPAddress

        for (String serverToTry : args) {
            if (connectToServer(serverToTry)) {
                break;
            }
        }

        System.out.println("Got reference of AlcatrazServer.");

        client.setName();

        // Client registers by the AlcatrazServer and sends its reference to AlcatrazServer

        try {
            alcatrazServerRef.register(client);
        } catch (Exception e) {
            System.err.printf("Error registering the client with the server: %s%n", e.getMessage());
            System.exit(3);
        }

        boolean running = true;
        while (running) {
            System.out.println("Please enter\n\t1 for sign out and exit\n\t2 for start the game\n\t3 for sign in or\n\t4 for sign out.\n");

            try {
                switch (Integer.valueOf(console.readLine())) {
                    case 1:
                        running = false;
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
                        client.setName();
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

        client.signOut();
    }

    /**
     * Try to connect to the given registry server
     *
     * @param serverAddress IP address or DNS name of the RMI server
     * @return true if successfully connected to the server, false in case of an error
     */
    private static boolean connectToServer(String serverAddress) {
        String rmiUrl = "//" + serverAddress + ":1099/AlcatrazServer";
        try {
            System.out.printf("Trying to connect to server: %s%n", serverAddress);
            alcatrazServerRef = (IServer) Naming.lookup(rmiUrl);
            return true;
        } catch (MalformedURLException e) {
            System.err.printf("Invalid RMI URL: %s (%s)%n", rmiUrl, e.getMessage());
            return false;
        } catch (Exception e) {
            System.err.printf("Error connecting to registry server %s: %s%n", serverAddress, e.getMessage());
            return false;
        }
    }
}
