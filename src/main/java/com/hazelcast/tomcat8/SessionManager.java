package com.hazelcast.tomcat8;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.Session;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

import java.io.IOException;
import java.util.ArrayList;

import org.apache.catalina.Valve;

import com.hazelcast.core.IMap;
import com.hazelcast.session.HazelcastSessionChangeValve;
import com.hazelcast.session.HazelcastSession;
import com.hazelcast.session.HazelcastSessionManager;

public class SessionManager extends HazelcastSessionManager {
    private final Log log = LogFactory.getLog(SessionManager.class);
    @Override
    public void startInternal() throws LifecycleException {
        super.startInternal();
        configureValves();
    }
    protected void configureValves() {                
        if (isSticky()) {
            log.info("configure valves");
            //remove legacy valve
            ArrayList<Valve> valvesToRemove = new ArrayList();
            for(Valve v : getContext().getPipeline().getValves()) {
                if (v instanceof HazelcastSessionChangeValve) {
                    valvesToRemove.add(v);
                }
            }
            log.info("valves to remove " + valvesToRemove.size());
            for(Valve v : valvesToRemove) {
                getContext().getPipeline().removeValve(v);
            }            
            //install new valve
            SessionChangeValve sessionChangeValve = new SessionChangeValve();
            sessionChangeValve.setAsyncSupported(true);
            getContext().getPipeline().addValve(sessionChangeValve);
        }
    }
    
    protected String getLocalSessionId(String id) {
        int index = id.indexOf(".");
        String base = (index >= 0) 
                ? id.substring(0, index) 
                : id;
        String route = getJvmRoute();
        return route != null && route.length() > 0 
                ? base + "." + route 
                : base;
    }
    
    protected void bindHzSessionToNode(HazelcastSession hzSession) {
        if (hzSession != null) { 
            String oldId = hzSession.getId();
            String newId = getLocalSessionId(oldId);
            hzSession.access();
            hzSession.endAccess();
            hzSession.setSessionManager(this);
            //save session for local access
            sessions.put(newId, hzSession);
            //change session id if jvmRoute
            if (!oldId.equalsIgnoreCase(newId)) {
                changeSessionId(hzSession, newId);
            }
            // call remove method to trigger eviction Listener on each node to invalidate local sessions
            IMap<String, HazelcastSession> hzMap = getDistributedMap();
            hzMap.remove(oldId);
            hzMap.set(newId, hzSession);
        }
    }
    
    private HazelcastSession getDistributedSession(String id) {
        log.debug("Try lookup session from Hazelcast map:" + getMapName() + " with session "+id);        
        HazelcastSession hzSession = getDistributedMap().get(id);
        if (hzSession != null) {
            log.info("Some failover occured so reading session from Hazelcast map");
            bindHzSessionToNode(hzSession);
        }
        return hzSession;
    }
    
    @Override
    public Session findSession(String id) throws IOException {
        log.debug("Attempting to find sessionId:" + id);        
        Session result = null;
        if (id != null) {            
            result = (!isSticky() || (isSticky() && !sessions.containsKey(id))) 
                    ? getDistributedSession(id)
                    : sessions.get(id);
        }
        if (result != null) {
            log.debug("leave " + id + " " + result.hashCode() + " " + result.getId());
        }        
        return result;
    }
}
