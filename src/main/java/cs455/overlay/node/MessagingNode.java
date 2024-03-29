package cs455.overlay.node;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import cs455.overlay.routing.RoutingEntry;
import cs455.overlay.routing.RoutingTable;
import cs455.overlay.transport.TCPConnection;
import cs455.overlay.transport.TCPConnectionsCache;
import cs455.overlay.transport.TCPServerThread;
import cs455.overlay.util.InteractiveCommandParser;
import cs455.overlay.wireformats.Event;
import cs455.overlay.wireformats.NodeReportsOverlaySetupStatus;
import cs455.overlay.wireformats.OverlayNodeReportsTaskFinished;
import cs455.overlay.wireformats.OverlayNodeReportsTrafficSummary;
import cs455.overlay.wireformats.OverlayNodeSendsData;
import cs455.overlay.wireformats.OverlayNodeSendsDeregistration;
import cs455.overlay.wireformats.OverlayNodeSendsRegistration;
import cs455.overlay.wireformats.Protocol;
import cs455.overlay.wireformats.RegistryReportsDeregistrationStatus;
import cs455.overlay.wireformats.RegistryReportsRegistrationStatus;
import cs455.overlay.wireformats.RegistryRequestsTaskInitiate;
import cs455.overlay.wireformats.RegistrySendsNodeManifest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MessagingNode implements Node {
    private static final Logger logger = LogManager.getLogger(MessagingNode.class);

    private TCPConnection registryConnection;
    private TCPServerThread tcpServerThread;
    private InteractiveCommandParser commandParser;
    private int nodeId; // randomly generated by the registry
    private RoutingTable routingTable;

    private AtomicInteger sendTracker;
    private AtomicInteger receiveTracker;
    private AtomicInteger relayTracker;
    private AtomicLong sendSummation;
    private AtomicLong receiveSummation;

    private int[] allNodeIds;
    private HashMap<Integer, Socket> connectedNodeIdSocketMap;

    private TCPConnectionsCache tcpConnectionsCache;

    public MessagingNode(Socket registrySocket) throws IOException {
        registryConnection = new TCPConnection(registrySocket, this);

        tcpConnectionsCache = new TCPConnectionsCache();
        tcpServerThread = new TCPServerThread(0, this, tcpConnectionsCache);

        commandParser = new InteractiveCommandParser(this);

        sendRegistrationRequestToRegistry();
        connectedNodeIdSocketMap = new HashMap<>();

        sendTracker = new AtomicInteger(0);
        receiveTracker = new AtomicInteger(0);
        relayTracker = new AtomicInteger(0);
        sendSummation = new AtomicLong(0);
        receiveSummation = new AtomicLong(0);

    }

    public void initialize() {
        tcpServerThread.start();
        commandParser.start();
    }

    public static void main(String[] args) throws IOException {

        if (args.length < 2) {
            logger.error("Not enough arguments to start messaging node. " +
                    "Please provide registryHost and registryPort.");
            System.exit(1);
        } else if (args.length > 2) {
            logger.error("Too many arguments. Only the registryHost and registryPort are needed.");
            System.exit(1);
        }

        // Input is OK. Create a new messaging node
        String registryHost = args[0];
        int registryPort = Integer.parseInt(args[1]);
        Socket socket = new Socket(registryHost, registryPort);
        MessagingNode node = new MessagingNode(socket);
        node.initialize();
        TCPConnection connection;
        if (node.tcpConnectionsCache.containsConnection(socket)) {
            connection = node.tcpConnectionsCache.getConnection(socket);
            logger.info("Connection found in TCPConnectionsCache");
        } else {
            logger.info("Connection not found in TCPConnectionsCache. " +
                    "Creating a new connection");
            connection = new TCPConnection(socket, node);
        }
    }

    public int getNodeId() {
        return nodeId;
    }

    public void setNodeId(int nodeId) {
        this.nodeId = nodeId;
    }

    @Override
    public void onEvent(Event event) {
        int type = event.getType();

        switch (type) {
            case Protocol.REGISTRY_REPORTS_REGISTRATION_STATUS:
                handleRegistryReportsRegistrationStatus(event);
                break;
            case Protocol.REGISTRY_REPORTS_DEREGISTRATION_STATUS:
                handleRegistryReportsDeregistrationStatus(event);
                break;
            case Protocol.REGISTRY_SENDS_NODE_MANIFEST:
                respondToRegistrySendsNodeManifest(event);
                break;
            case Protocol.REGISTRY_REQUESTS_TASK_INITIATE:
                initiateTask(event);
                break;
            case Protocol.REGISTRY_REQUESTS_TRAFFIC_SUMMARY:
                sendTaskSummaryToRegistry(event);
                break;
            case Protocol.OVERLAY_NODE_SENDS_DATA:
                respondToOverlayNodeSendsData(event);
                break;
            default:
                logger.error("Unknown event type: " + type);
        }
    }


    private void sendTaskSummaryToRegistry(Event event) {
        OverlayNodeReportsTrafficSummary trafficSummaryEvent = new OverlayNodeReportsTrafficSummary();
        trafficSummaryEvent.setNodeId(nodeId);
        trafficSummaryEvent.setNumPacketsReceived(receiveTracker.get());
        trafficSummaryEvent.setNumPacketsSent(sendTracker.get());
        trafficSummaryEvent.setNumPacketsRelayed(relayTracker.get());
        trafficSummaryEvent.setSumPacketsReceived(receiveSummation.get());
        trafficSummaryEvent.setSumPacketsSent(sendSummation.get());

        System.out.println("\n========================================");
        logger.info("Node ID: " + nodeId);
        logger.info("receiveTracker: " + receiveTracker);
        logger.info("sendTracker :" + sendTracker);
        logger.info("relayTracker: " + relayTracker);
        logger.info("sendSummation: " + sendSummation);
        logger.info("receiveSummation: " + receiveSummation);
        System.out.println("========================================\n");

        try {
            registryConnection.sendData(trafficSummaryEvent.getBytes());
        } catch (IOException e) {
            logger.error(e.getStackTrace());
        }
    }

    /**
     * byte: Message type; REGISTRY_REQUESTS_TASK_INITIATE
     * int: Number of data packets to send
     */
    private void initiateTask(Event event) {
        logger.info("Node " + nodeId + " starting to send messages");
        sendTracker.set(0);
        receiveTracker.set(0);
        relayTracker.set(0);
        sendSummation.set(0);
        receiveSummation.set(0);

        RegistryRequestsTaskInitiate taskInitiateEvent = (RegistryRequestsTaskInitiate) event;
        int noOfPacketsToSend = taskInitiateEvent.getNoOfPacketsToSend();
        Random random = new Random();

        logger.info("Source ID: " + getNodeId());
        OverlayNodeSendsData sendsDataEvent;
        for (int i = 0; i < noOfPacketsToSend; i++) {
            sendsDataEvent = new OverlayNodeSendsData();
            sendsDataEvent.setSourceId(getNodeId());

            int payload = random.nextInt();
            sendsDataEvent.setPayload(payload);

            // select a node at random from the nodes in the network
            int destinationNodeIdPosition = random.nextInt(allNodeIds.length);
            int destinationNodeId = allNodeIds[destinationNodeIdPosition];
            logger.info("Destination ID: " + destinationNodeId);

            // avoid sending packet to the node itself
            while (getNodeId() == destinationNodeId) {
                destinationNodeIdPosition = random.nextInt(allNodeIds.length);
                destinationNodeId = allNodeIds[destinationNodeIdPosition];
            }

            sendsDataEvent.setDestinationId(destinationNodeId);
            sendsDataEvent.setPayload(payload);

            // check routing table
            RoutingEntry routingEntry;
            if (routingTable.containsNodeId(destinationNodeId)) {
                // send directly
                logger.debug("Destination node " + destinationNodeId + " found in node " +
                        getNodeId() + "'s routing table. Sending directly.");
                routingEntry = routingTable.getRoutingEntry(destinationNodeId);
            } else {
                // destination not found in the routing table
                logger.debug("Destination node " + destinationNodeId + " not found in node " +
                        getNodeId() + "'s routing table.");
                int nextBestNode = routingTable.
                        getNextBestNode(sendsDataEvent, allNodeIds);
                routingEntry = routingTable.getRoutingEntry(nextBestNode);
            }

            Socket socket = routingEntry.getSocket();
            TCPConnection tcpConnection = tcpConnectionsCache.getConnection(socket);
            try {
                tcpConnection.sendData(sendsDataEvent.getBytes());
                sendTracker.getAndIncrement();
                sendSummation.getAndAdd(payload);
            } catch (IOException e) {
                logger.error(e.getStackTrace());
            }
        }
        reportTaskFinished();
    }

    private void reportTaskFinished() {
        OverlayNodeReportsTaskFinished event = new OverlayNodeReportsTaskFinished();
        event.setIpAddressLength((byte) registryConnection.getLocalAddress().length);
        event.setIpAddress(registryConnection.getLocalAddress());
        event.setPort(tcpServerThread.getListeningPort());
        event.setNodeId(nodeId);

        try {
            registryConnection.sendData(event.getBytes());
        } catch (IOException e) {
            logger.error(e.getStackTrace());
        }
    }

    /**
     * byte: Message type; REGISTRY_SENDS_NODE_MANIFEST
     * byte: routing table size N R
     * =============================================================================
     * int: Node ID of node 1 hop away
     * byte: length of following "IP address" field
     * byte[^^]: IP address of node 1 hop away; from InetAddress.getAddress()
     * int: Port number of node 1 hop away
     * -----------------------------------------------------------------------------
     * int: Node ID of node 2 hops away
     * byte: length of following "IP address" field
     * byte[^^]: IP address of node 2 hops away; from InetAddress.getAddress()
     * int: Port number of node 2 hops away
     * -----------------------------------------------------------------------------
     * int: Node ID of node 4 hops away
     * byte: length of following "IP address" field
     * byte[^^]: IP address of node 4 hops away; from InetAddress.getAddress()
     * int: Port number of node 4 hops away
     * =============================================================================
     * byte: Number of node IDs in the system
     * int[^^]: List of all node IDs in the system [Note no IPs are included]
     */
    private void respondToRegistrySendsNodeManifest(Event event) {
        RegistrySendsNodeManifest nodeManifestEvent = (RegistrySendsNodeManifest) event;
        int tableSize = nodeManifestEvent.getTableSize();
        logger.info("tableSize: " + tableSize);
        routingTable = new RoutingTable(tableSize);
        ArrayList<RoutingEntry> routingEntries = routingTable.getRoutingEntries();
        for (int i = 0; i < tableSize; i++) {
            routingTable.addRoutingEntry(new RoutingEntry(
                    (int) Math.pow(2, i),
                    nodeManifestEvent.getNodesIds()[i],
                    new String(nodeManifestEvent.getIpAddresses()[i]),
                    nodeManifestEvent.getPorts()[i]
            ));
            logger.info("IP Address received: " + new String(nodeManifestEvent.getIpAddresses()[i]));
        }
        logger.info("No. of Routing Entries: " + routingEntries.size());
        System.out.println("\n\nRouting Table of node " + nodeId);
        System.out.println("--------------------------------------");
        routingTable.printRoutingTable();
        System.out.println("--------------------------------------");

        int noOfAllNodeIds = nodeManifestEvent.getNoOfAllNodeIds();
        allNodeIds = nodeManifestEvent.getAllNodeIds();

        connectToNodesInRoutingTable(routingTable);

        // prepare response event
        NodeReportsOverlaySetupStatus responseEvent = new NodeReportsOverlaySetupStatus();
        responseEvent.setSuccessStatus(getNodeId());
        String infoString = "Node " + getNodeId() + " successfully initiated connections with all" +
                " nodes in the routing table.";
        responseEvent.setLengthOfInfoString((byte) infoString.getBytes().length);
        responseEvent.setInfoString(infoString);

        try {
            registryConnection.sendData(responseEvent.getBytes());
        } catch (IOException e) {
            logger.error("Error sending data to Registry");
            logger.error(e.getStackTrace());
        }
    }

    private void connectToNodesInRoutingTable(RoutingTable routingTable) {
        ArrayList<RoutingEntry> routingEntries = routingTable.getRoutingEntries();
        for (RoutingEntry routingEntry : routingEntries) {
            System.out.println("\n\nConnecting to node: " + routingEntry.getNodeId());
            try {
                logger.info("IPAddress: " + InetAddress.getByName(routingEntry.getIpAddress()));
                logger.info("Port: " + routingEntry.getPort());
                InetAddress byAddress = InetAddress.getByName(routingEntry.getIpAddress());
                Socket socket = new Socket(byAddress.getHostAddress(), routingEntry.getPort());
                TCPConnection tcpConnection = new TCPConnection(socket, this);
                tcpConnectionsCache.addConnection(socket, tcpConnection);
                routingEntry.setSocket(socket);
                connectedNodeIdSocketMap.put(routingEntry.getNodeId(), socket);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    private void handleRegistryReportsRegistrationStatus(Event event) {
        RegistryReportsRegistrationStatus registrationStatus =
                (RegistryReportsRegistrationStatus) event;
        int successStatus = registrationStatus.getSuccessStatus();
        if (successStatus == -1) {
            logger.info("Registration failed!");
            logger.info(registrationStatus.getInfoString());
            System.exit(-1);
        } else {
            logger.info("Registration successful!");
            setNodeId(successStatus);
            logger.info(registrationStatus.getInfoString());
        }
    }

    private void handleRegistryReportsDeregistrationStatus(Event event) {
        RegistryReportsDeregistrationStatus deregistrationStatus =
                (RegistryReportsDeregistrationStatus) event;
        int successStatus = deregistrationStatus.getSuccessStatus();
        if (successStatus == -1) {
            logger.info("Deregistration failed");
            logger.info(deregistrationStatus.getInfoString());
        } else if (successStatus == getNodeId()) {
            logger.info("Deregistration successful!");
            logger.info(deregistrationStatus.getInfoString());
            commandParser.stopAcceptingCommands();
            Socket socket = deregistrationStatus.getSocket();
            logger.info("Stopping node ...");
            System.exit(0);
        } else {
            logger.warn("Deregistration failed. Reason unknown.");
        }
    }

    /**
     * byte: Message Type (OVERLAY_NODE_SENDS_REGISTRATION)
     * byte: length of following "IP address" field
     * byte[^^]: IP address; from InetAddress.getAddress()
     * int: Port number
     */
    private void sendRegistrationRequestToRegistry() throws IOException {
        OverlayNodeSendsRegistration message = new OverlayNodeSendsRegistration();
        message.setIpAddressLength((byte) registryConnection.getSocket().
                getLocalAddress().getAddress().length);
        message.setIpAddress(registryConnection.getSocket().
                getLocalAddress().getAddress());
        message.setPort(tcpServerThread.getListeningPort());
        message.setSocket(registryConnection.getSocket());

        registryConnection.sendData(message.getBytes());
    }

    /**
     * byte: Message Type (OVERLAY_NODE_SENDS_DEREGISTRATION)
     * byte: length of following "IP address" field
     * byte[^^]: IP address; from InetAddress.getAddress()
     * int: Port number
     */
    private void sendDeregistrationRequestToRegistry() throws IOException {
        OverlayNodeSendsDeregistration deregistrationEvent = new OverlayNodeSendsDeregistration();
        deregistrationEvent.setIpAddressLength((byte) registryConnection.getSocket().
                getLocalAddress().getAddress().length);
        deregistrationEvent.setIpAddress(registryConnection.getSocket().
                getLocalAddress().getAddress());
        deregistrationEvent.setPort(tcpServerThread.getListeningPort());
        deregistrationEvent.setSocket(registryConnection.getSocket());
        deregistrationEvent.setNodeId(getNodeId());

        registryConnection.sendData(deregistrationEvent.getBytes());
    }

    private void respondToOverlayNodeSendsData(Event event) {
        OverlayNodeSendsData nodeSendsDataEvent = (OverlayNodeSendsData) event;
        nodeSendsDataEvent.setDisseminationTraceLength(nodeSendsDataEvent.
                getDisseminationTraceLength() + 1);

        int destinationId = nodeSendsDataEvent.getDestinationId();
        if (destinationId == nodeId) {
            // current node is packet's destination
            receiveTracker.getAndIncrement();
            receiveSummation.getAndAdd(nodeSendsDataEvent.getPayload());
        } else {
            // current node is not the destination
            // check if the destination is found in current node's routing table
            int nodeToSend;
            if (routingTable.containsNodeId(destinationId)) {
                nodeToSend = destinationId;
            } else {
                // not found in routing table
                nodeSendsDataEvent.setSourceId(nodeId);
                nodeToSend = routingTable.getNextBestNode(nodeSendsDataEvent, allNodeIds);
            }
            Socket socket = connectedNodeIdSocketMap.get(nodeToSend);
            TCPConnection tcpConnection = tcpConnectionsCache.getConnection(socket);
            try {
                tcpConnection.sendData(nodeSendsDataEvent.getBytes());
            } catch (IOException e) {
                logger.error(e.getStackTrace());
            }

            relayTracker.getAndIncrement();
        }
    }

    public void exitOverlay() throws IOException {
        sendDeregistrationRequestToRegistry();
    }

    public void printRoutingTable() {
        System.out.println("Routing Table for node " + getNodeId());
        routingTable.printRoutingTable();
    }

    public void printNodeId() {
        System.out.println("Node ID: " + nodeId);
    }
}
