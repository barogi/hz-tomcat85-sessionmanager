package com.hazelcast.tomcat8;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;

import javax.servlet.ServletException;

import java.io.IOException;

import org.apache.catalina.Session;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

class SessionChangeValve extends ValveBase {

    private final Log log = LogFactory.getLog(SessionChangeValve.class);

    @Override
    public void invoke(Request request, Response response) 
            throws IOException, ServletException {
        Session session = request.getSessionInternal(false);
        String requestedSessionId = request.getRequestedSessionId();
        String sessionId = session != null
                ? session.getId()
                : null;
        if (sessionId != null && requestedSessionId != null &&
                !requestedSessionId.equalsIgnoreCase(sessionId)) {
            log.debug("handle failover  " + requestedSessionId + " " + sessionId);
            request.changeSessionId(sessionId);
        }
        getNext().invoke(request, response);
    }
}
