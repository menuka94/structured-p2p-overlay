package cs455.overlay.node;

import java.io.IOException;
import java.net.Socket;

import cs455.overlay.transport.TCPConnection;
import cs455.overlay.transport.TCPConnectionsCache;
import cs455.overlay.transport.TCPServerThread;
import cs455.overlay.util.InteractiveCommandParser;
import cs455.overlay.wireformats.Event;
import cs455.overlay.wireformats.OverlayNodeSendsRegistration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MessagingNode implements Node {
    private static final Logger logger = LogManager.getLogger(MessagingNode.class);

    private TCPConnection registryConnection;
    private TCPServerThread tcpServerThread;
    private TCPConnectionsCache tcpConnectionsCache;
    private InteractiveCommandParser commandParser;
    private int id;

    public MessagingNode(Socket registrySocket) throws IOException {
        registryConnection = new TCPConnection(registrySocket, this);

        tcpServerThread = new TCPServerThread(0, this);
        tcpServerThread.start();

        commandParser = new InteractiveCommandParser(this);
        commandParser.start();

        sendRegistrationRequestToRegistry();
    }

    public static void main(String[] args) throws IOException {
        String registryHost = args[0];
        int registryPort = Integer.parseInt(args[1]);

        if (args.length < 2) {
            logger.error("Not enough arguments to start messaging node. Please provide registryHost and registryPort.");
            System.exit(1);
        } else if (args.length > 2) {
            logger.error("Too many arguments. Only the registryHost and registryPort are needed.");
            System.exit(1);
        }

        // Input is OK. Create a new messaging node
        Socket socket = new Socket(registryHost, registryPort);
        MessagingNode node = new MessagingNode(socket);
        TCPConnection connection;
        if (TCPConnectionsCache.containsConnection(socket)) {
            connection = TCPConnectionsCache.getConnection(socket);
            logger.info("Connection found in TCPConnectionsCache");
        } else {
            logger.info("Connection not found in TCPConnectionsCache." +
                    "Creating a new connection");
            connection = new TCPConnection(socket, node);
        }

    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    @Override
    public void onEvent(Event event) {

    }

    /**
     * byte: Message Type (OVERLAY_NODE_SENDS_REGISTRATION)
     * byte: length of following "IP address" field
     * byte[^^]: IP address; from InetAddress.getAddress()
     * int: Port number
     */
    private void sendRegistrationRequestToRegistry() throws IOException {
        logger.info("sendRegistrationRequestToRegistry()");
        OverlayNodeSendsRegistration message = new OverlayNodeSendsRegistration();
        message.setIpAddressLength((byte) registryConnection.getSocket().
                getLocalAddress().getAddress().length);
        message.setIpAddress(registryConnection.getSocket().
                getLocalAddress().getAddress());
        message.setPort(tcpServerThread.getListeningPort());
        if (registryConnection.getSocket() == null) {
            logger.info("Registry socket is null");
        } else {
            logger.info("Registry socket is NOT null");
        }
        message.setSocket(registryConnection.getSocket());

        registryConnection.sendData(message.getBytes());
    }
}
