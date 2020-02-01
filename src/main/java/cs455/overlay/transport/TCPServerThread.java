package cs455.overlay.transport;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TCPServerThread extends Thread {
    private static final Logger logger = LogManager.getLogger(TCPServerThread.class);
    private boolean listeningForClients;
    private ServerSocket serverSocket;

    public TCPServerThread(int listenPort) throws IOException {
        serverSocket = new ServerSocket(listenPort);
    }

    @Override
    public void run() {
        listeningForClients = true;

        while (listeningForClients) {
            try {
                logger.info("Server accepting connections ...");
                Socket socket = serverSocket.accept();
                TCPConnection tcpConnection = new TCPConnection(socket);
                logger.info("socket.getInetAddress(): " + socket.getInetAddress());
                TCPConnectionsCache.addConnection(socket.getInetAddress().getHostAddress(),
                        tcpConnection);
                logger.info("New Connection Established");
            } catch (IOException e) {
                logger.error(e.getStackTrace());
            }
        }

        try {
            serverSocket.close();
        } catch (IOException e) {
            logger.error(e.getStackTrace());
        }
    }

    public void stopListeningForClients() {
        listeningForClients = false;
    }

}