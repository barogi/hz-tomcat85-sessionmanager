package com.hazelcast.tomcat8;

import javax.servlet.ServletException;

import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Session;
import org.apache.catalina.startup.Tomcat;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.hazelcast.session.HazelcastSession;

import static org.junit.Assert.*;

import com.hazelcast.session.P2PLifecycleListener;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;

/**
 *
 * @author igor.burenkov
 */
public class SessionManagerTest {
    
    static SessionManager sessionManager;
    
    static class SessionManager2 extends SessionManager {
        @Override
        protected void setState(LifecycleState state) {                
        }
    };
    
    @BeforeClass
    public static void beforeClass() 
            throws LifecycleException, ServletException {        
        Tomcat tomcat = new Tomcat();        
        tomcat.getEngine().setJvmRoute("local");                
        sessionManager = spy(SessionManager2.class);
        sessionManager.setContext(tomcat.addWebapp("/", "./"));
        sessionManager.setSticky(true);
        sessionManager.setDeferredWrite(true);
        new P2PLifecycleListener().lifecycleEvent(new LifecycleEvent(sessionManager, "start", null));
        sessionManager.startInternal();        
    }    
    
    @AfterClass
    public static void afterClass() 
            throws LifecycleException {
        new P2PLifecycleListener().lifecycleEvent(new LifecycleEvent(sessionManager, "stop", null));
    }

    @Test
    public void testGetLocalSessionId() {
        assertEquals("session.local", sessionManager.getLocalSessionId("session.local"));
        assertEquals("session.local", sessionManager.getLocalSessionId("session.jvm2"));
    }
    
    @Test
    public void testFindSession_Local() throws Exception {
        String id = "testFindSession_Local.local";
        Session ss = sessionManager.createSession(id);
        reset(sessionManager);
        assertEquals(ss, sessionManager.findSession(id));
        verify(sessionManager, times(0)).getDistributedSession((String)any());
    }
    
    @Test
    public void testFindSession_Null() throws Exception {        
        assertNull(sessionManager.findSession(null));
        verify(sessionManager, times(0)).getDistributedSession((String)any());
    }
    
    @Test
    public void testFindSession_Distributed() throws Exception {
        HazelcastSession ss = new HazelcastSession();
        ss.setId("testFindSession_Distributed.124", false);        
        sessionManager.getDistributedMap().set("testFindSession_Distributed.124", ss);
        
        reset(sessionManager);
        Session s0 = sessionManager.findSession("testFindSession_Distributed.124");        
        assertNotNull(s0);
        assertEquals(s0.getId(), "testFindSession_Distributed.local");
        verify(sessionManager, times(1)).getDistributedSession((String)any());
    }
    
    @Test
    public void testGetDistributedSession() throws Exception {        
        //create session
        HazelcastSession ss = new HazelcastSession();
        ss.setId("testGetDistributedSession.124", false);
        sessionManager.getDistributedMap().set("testGetDistributedSession.124", ss);
        
        //lookup and relocate
        reset(sessionManager);
        Session ds = sessionManager.findSession("testGetDistributedSession.124");        
        verify(sessionManager, times(1)).getDistributedSession((String)any());
        assertEquals(ds.getId(), "testGetDistributedSession.local");
                
        //check relocation
        reset(sessionManager);
        assertNotNull(sessionManager.findSession("testGetDistributedSession.local"));
        verify(sessionManager, times(0)).getDistributedSession((String)any());
    }
}
