package org.directtruststandards.timplus.server.handlers;

import org.dom4j.Element;
import org.jivesoftware.openfire.IQHandlerInfo;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.domain.DomainManager;
import org.jivesoftware.openfire.handler.IQHandler;
import org.jivesoftware.openfire.admin.AdminManager;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.PacketError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Custom IQ handler to allow XMPP clients to create new domains
 * Namespace: urn:timplus:domain:create
 */
public class DomainCreationIQHandler extends IQHandler {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(DomainCreationIQHandler.class);
    
    private static final String NAMESPACE = "urn:timplus:domain:create";
    private static final String ELEMENT_NAME = "create-domain";
    
    private IQHandlerInfo info;
    
    public DomainCreationIQHandler() {
        super("Domain Creation Handler");
        info = new IQHandlerInfo(ELEMENT_NAME, NAMESPACE);
    }
    
    @Override
    public IQ handleIQ(IQ packet) throws UnauthorizedException {
        IQ reply = IQ.createResultIQ(packet);
        
        if (packet.getType() != IQ.Type.set) {
            reply.setError(PacketError.Condition.bad_request);
            return reply;
        }
        
        Element childElement = packet.getChildElement();
        if (childElement == null) {
            reply.setError(PacketError.Condition.bad_request);
            return reply;
        }
        
        String domainName = childElement.elementTextTrim("domain");
        if (domainName == null || domainName.isEmpty()) {
            reply.setError(PacketError.Condition.bad_request);
            return reply;
        }
        
        try {
            // Check if user has admin privileges (optional security check)
            JID fromJID = packet.getFrom();
            if (!AdminManager.getInstance().isUserAdmin(fromJID, true)) {
                reply.setError(PacketError.Condition.forbidden);
                return reply;
            }
            
            // Check if domain already exists
            if (DomainManager.getInstance().isRegisteredDomain(domainName)) {
                reply.setError(PacketError.Condition.conflict);
                return reply;
            }
            
            // Validate domain name format
            if (!isValidDomainName(domainName)) {
                reply.setError(PacketError.Condition.not_acceptable);
                return reply;
            }
            
            // Create the domain
            DomainManager.getInstance().createDomain(domainName, true);
            
            LOGGER.info("Domain '{}' created by client '{}'", domainName, packet.getFrom());
            
            // Return success response
            Element result = reply.setChildElement(ELEMENT_NAME, NAMESPACE);
            result.addElement("domain").setText(domainName);
            result.addElement("status").setText("created");
            
        } catch (Exception e) {
            LOGGER.error("Failed to create domain: " + domainName, e);
            reply.setError(PacketError.Condition.internal_server_error);
        }
        
        return reply;
    }
    
    @Override
    public IQHandlerInfo getInfo() {
        return info;
    }
    
    private boolean isValidDomainName(String domain) {
        // Basic domain validation
        return domain.matches("^[a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?(\\.[a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?)*$");
    }
}