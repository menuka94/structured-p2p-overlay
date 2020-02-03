package cs455.overlay.wireformats;

import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import cs455.overlay.node.MessagingNode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class EventFactory {
    private static final Logger logger = LogManager.getLogger(EventFactory.class);
    private static EventFactory instance;

    private EventFactory() {

    }

    public static EventFactory getInstance() {
        instance = new EventFactory();
        return instance;
    }

    public Event getEvent(byte[] data, Socket socket, MessagingNode messagingNode)
            throws IOException {

        switch (ByteBuffer.wrap(data).get(0)) {
            case Protocol.OVERLAY_NODE_SENDS_DATA:
                return new OverlayNodeSendsData(data);
            case Protocol.OVERLAY_NODE_SENDS_REGISTRATION:
                OverlayNodeSendsRegistration event = new OverlayNodeSendsRegistration(data, messagingNode);
                event.setSocket(socket);
                return event;
            case Protocol.REGISTRY_REPORTS_REGISTRATION_STATUS:
                return new RegistryReportsRegistrationStatus(data);
            case Protocol.OVERLAY_NODE_SENDS_DEREGISTRATION:
                return new OverlayNodeSendsDeregistration(data);
            case Protocol.REGISTRY_REPORTS_DEREGISTRATION_STATUS:
                return new RegistryReportsDeregistrationStatus(data);
            case Protocol.REGISTRY_SENDS_NODE_MANIFEST:
                return new RegistrySendsNodeManifest(data);
            case Protocol.NODE_REPORTS_OVERLAY_SETUP_STATUS:
                return new NodeReportsOverlaySetupStatus(data);
            case Protocol.REGISTRY_REQUESTS_TASK_INITIATE:
                return new RegistryRequestsTaskInitiate(data);
            case Protocol.OVERLAY_NODE_REPORTS_TASK_FINISHED:
                return new OverlayNodeReportsTaskFinished(data);
            case Protocol.REGISTRY_REQUESTS_TRAFFIC_SUMMARY:
                return new RegistryRequestsTrafficSummary(data);
            case Protocol.OVERLAY_NODE_REPORTS_TRAFFIC_SUMMARY:
                return new OverlayNodeReportsTrafficSummary(data);
            default:
                logger.error("Unknown event type");
                return null;
        }

    }
}
