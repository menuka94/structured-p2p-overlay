package cs455.overlay.wireformats;

import cs455.overlay.util.Validator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;


public class OverlayNodeSendsRegistration extends Event {
    private static final Logger logger = LogManager.getLogger(OverlayNodeSendsRegistration.class);

    private byte ipAddressLength;
    private byte[] ipAddress;
    private int port;
    private Socket socket;

    public OverlayNodeSendsRegistration() {

    }

    public Socket getSocket() {
        return socket;
    }

    public void setSocket(Socket socket) {
        this.socket = socket;
    }

    /**
     * byte: Message Type (OVERLAY_NODE_SENDS_REGISTRATION)
     * byte: length of following "IP address" field
     * byte[^^]: IP address; from InetAddress.getAddress()
     * int: Port number
     */
    public OverlayNodeSendsRegistration(byte[] marshalledBytes) throws IOException {
        ByteArrayInputStream baInputStream = new ByteArrayInputStream(marshalledBytes);
        DataInputStream din = new DataInputStream(new BufferedInputStream(baInputStream));

        byte messageType = din.readByte();

        Validator.validateEventType(messageType, Protocol.OVERLAY_NODE_SENDS_REGISTRATION, logger);

        ipAddressLength = din.readByte();
        ipAddress = new byte[ipAddressLength];
        din.readFully(ipAddress, 0, ipAddressLength);
        port = din.readInt();

        baInputStream.close();
        din.close();
    }

    public void setIpAddressLength(byte ipAddressLength) {
        this.ipAddressLength = ipAddressLength;
    }

    public void setIpAddress(byte[] ipAddress) {
        this.ipAddress = ipAddress;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public byte getIpAddressLength() {
        return ipAddressLength;
    }

    public byte[] getIpAddress() {
        return ipAddress;
    }

    public int getPort() {
        return port;
    }

    @Override
    public byte[] getBytes() {
        byte[] marshalledBytes = null;
        ByteArrayOutputStream baOutputStream = new ByteArrayOutputStream();
        DataOutputStream dout = new DataOutputStream(new BufferedOutputStream(baOutputStream));

        try {
            dout.writeByte(getType());
            dout.writeByte(ipAddressLength);
            dout.write(ipAddress);
            dout.writeInt(port);

            dout.flush();

            marshalledBytes = baOutputStream.toByteArray();
        } catch (IOException e) {
            logger.error(e.getStackTrace());
        } finally {
            try {
                baOutputStream.close();
                dout.close();
            } catch (IOException e) {
                logger.error(e.getStackTrace());
            }
        }

        return marshalledBytes;
    }


    @Override
    public int getType() {
        return Protocol.OVERLAY_NODE_SENDS_REGISTRATION;
    }
}
