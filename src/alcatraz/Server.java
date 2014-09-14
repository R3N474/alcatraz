/*
* IT Security Narmandakh Chuluunbaatar "AlcatrazServer"
*/

package alcatraz;

import spread.*;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A test class initializing a local Alcatraz game -- illustrating how
 * to use the Alcatraz API.
 */
public class Server implements IServer, BasicMessageListener {

    public static final String SERVER_GROUP = "ServerGroup";
    private final String serverId;

//    private int counter = 0;
//    private int sendFlag = 0, sendMessage = 0;

    private static final int MESSAGE_TYPE_UPDATE = 10;
    private static final int MESSAGE_TYPE_SIGN_OUT = 20;
    private static final int MESSAGE_TYPE_START_GAME = 30;

    private final Map<String, IClient> clients = new LinkedHashMap<String, IClient>(4);

//    private final IClient[] clientRefList = new IClient[4]; // save client references   
//    private IClient tempClientRef = null;

    private final SpreadConnection connection;
    private final SpreadGroup group;

    public Server(String serverId, SpreadConnection connection, SpreadGroup group) {
        this.serverId = serverId;
        this.connection = connection;
        this.group = group;
    }

    public void connectAndJoinGroup() throws SpreadException {
        System.out.println("Registering server as spread message listener");
        this.connection.add(this);
        System.out.println("Connecting to spread daemon");
        this.connection.connect(null, 0, "name", false, false);
        System.out.println("Joining spread group " + SERVER_GROUP);
        this.group.join(this.connection, SERVER_GROUP);
    }

    /**
     * Send multicast message to all clients
     */
    public void multicast(IClient ref, int type) {
        SpreadMessage message = new SpreadMessage();

        message.addGroup(group);
        message.setCausal();                // Causal ordered
        message.setSafe();                    // Total ordered and virtual synchrony
        message.setType((short) type);

        try {
            System.out.printf("Sending spread multicast message type %d%n", type);
            message.setObject(ref);
            connection.multicast(message);
            System.out.println("Message sent");
        } catch (SpreadException e) {
            System.err.println("Error sending multicast message: " + e.getMessage());
        }
    }

    /**
     * Handle message from other servers (via spread)
     */
    public void messageReceived(SpreadMessage message) {
        IClient client;
        try {
            client = (IClient) message.getObject();
        } catch (SpreadException e) {
            System.err.println("Error while receiving spread message: " + e.getMessage());
            return;
        }

        switch (message.getType()) {
            case MESSAGE_TYPE_START_GAME:
                handleStartGameMessage(client);
                break;
            case MESSAGE_TYPE_UPDATE:
                handleUpdateMessage(client);
                break;
            case MESSAGE_TYPE_SIGN_OUT:
                handleSignOutMessage(client);
                break;
            default:
                System.err.printf("Received unknown message type: %s%n", message.getType());
        }
    }

    private void handleSignOutMessage(IClient msg) {
        try {
            String name = msg.getName();
            if (clients.containsKey(name)) {
                clients.remove(name);
                System.out.printf("Client %s signed out %n", name);
            } else {
                System.out.printf("Not such client: %s%n", name);
            }
        } catch (RemoteException e) {
            System.err.println("Error while trying to sign out a client: " + e.getMessage());
        }
    }

    /**
     * AS checks each name of client, because the name of the clients should be unique.
     * If the name is unique, AS sends an update to all server includes itself.
     */
    public void register(IClient ref) throws RemoteException, Exception {
        String name = ref.getName();
        if (clients.containsKey(name)) {
            throw new IllegalArgumentException(String.format("Name \"%s\" already in use.", name));
        }
        multicast(ref, MESSAGE_TYPE_UPDATE);

//        System.out.println("Server checks the name of client: " + name);
//        for (IClient interfClient : clientRefList) {
//            if (interfClient.getName().equals(name)) {
//                throw new IllegalArgumentException(String.format("Name \"%s\" already in use.", name));
//            }
//        }
//        sendFlag = 1;
//        System.out.println("Server sends the updates to replicas in the group.");
//        multicast(ref, MESSAGE_TYPE_UPDATE);
    }

    /**
     * Handle a psread message to update the client references on all clients
     */
    private void handleUpdateMessage(IClient client) {
        // Each Server gets the update and saves the reference of client
        // AlcatrazServer, which the update sent, starts the game when four players have already registered successfully.

        try {
            String name = client.getName();
            System.out.println("Updating client: " + client.getName());
            clients.put(name, client);
            client.saveServerRef(this);
        } catch (RemoteException e) {
            System.err.println("Error updating client: " + e.getMessage());
        }

//        if (clients.size() >= 4) {
//            
//        }
//        
//        if (counter >= 4 && sendFlag == 1) {
//            System.out.println("Server starts the game.");
//            tempClientRef = client;
//            multicast(client, MESSAGE_TYPE_START_GAME);
//        }
//        sendFlag = 0;
    }

    /**
     * Handle a spread message to start the game
     */
    private void handleStartGameMessage(IClient msg) {
//        if (counter > 4) counter = 4;            // max player-number is 4
//        try {
//            if (tempClientRef != null) {
//                if (tempClientRef.getName().equals(msg.getName()) && sendMessage == 0) {
//                    if (tempClientRef != null)
//                        System.out.println(msg.getName() + " starts the game.\n");
//                    for (int i = 0; i < counter; i++)
//                        clientRefList[i].deliverReference(clientRefList, counter);
//                }
//            }
//        } catch (RemoteException e) {
//            System.err.println("Error starting the game: " + e.getMessage());
//        }
//
//        for (int i = 0; i < counter; i++) clientRefList[i] = null;        // delete the client - references
//
//        sendMessage = 1;
//        counter = 0;
    }

    /**
     * Register the client with the server
     * AlcatrazServer sends all client-references and the number of player to each client.
     */
    public void startAlcatraz(IClient ref) throws RemoteException, Exception {
        if (clients.size() <= 1) {
            throw new IllegalStateException("Need more than 1 player to start the game.");
        }

        multicast(ref, MESSAGE_TYPE_START_GAME);
    }

    public void signOut(IClient ref) throws RemoteException {
        if (clients.containsKey(ref.getName())) {
            multicast(ref, MESSAGE_TYPE_SIGN_OUT);
        }
//        for (int i = 0; i < counter; i++) {
//            if (clientRefList[i].getName().equals(ref.getName())) {
//                System.out.println("Server sends SignOut Message to Replika.");
//                multicast(ref, MESSAGE_TYPE_SIGN_OUT);
//
//                return;
//            }
//        }
    }

    @Override
    public String toString() {
        return serverId;
    }

    public static void main(String[] args) {
        Server server = new Server(args[0], new SpreadConnection(), new SpreadGroup());

        try {
            // Spread
            server.connectAndJoinGroup();
        } catch (SpreadException e) {
            System.err.println("Error joining spread group: " + e.getMessage());
            System.exit(3);
        }

        // Registry-Server saves the references of alcatrazServer. 
        // Default port of Registry-Server is 1099.
        try {
            registerWithRMI(server);
        } catch (RemoteException e) {
            System.err.println("Error creating/obtaining RMI Registry: " + e.getMessage());
            System.exit(4);
        }
    }

    private static void registerWithRMI(Server server) throws RemoteException {
        Registry registryServer;
        try {
            registryServer = LocateRegistry.createRegistry(1099);
        } catch (Exception e) {
            registryServer = LocateRegistry.getRegistry(1099);
        }

        // The service-name (AlcatrazServer) is bind / replaced with the remote-object reference. 
        // Port of the AlcatrazServer is: random and free port
        registryServer.rebind("AlcatrazServer", UnicastRemoteObject.exportObject(server, 0));
        System.out.println("AlcatrazServer" + server + " is registered by RegistryServer");
    }

}
