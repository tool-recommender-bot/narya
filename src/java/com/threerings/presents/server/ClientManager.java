//
// $Id: ClientManager.java,v 1.10 2001/08/07 21:20:48 mdb Exp $

package com.threerings.cocktail.cher.server;

import java.io.IOException;
import java.util.HashMap;

import com.threerings.cocktail.cher.Log;
import com.threerings.cocktail.cher.data.ClientObject;
import com.threerings.cocktail.cher.net.Credentials;
import com.threerings.cocktail.cher.server.net.*;

/**
 * The client manager is responsible for managing the clients (surprise,
 * surprise) which are slightly more than just connections. Clients
 * persist in the absence of connections in case a user goes bye bye
 * unintentionally and wants to reconnect and continue their session.
 *
 * <p> The client manager operates with thread safety because it is called
 * both from the conmgr thread (to notify of connections showing up or
 * going away) and from the dobjmgr thread (when clients are given the
 * boot for application-defined reasons).
 */
public class ClientManager implements ConnectionObserver
{
    public ClientManager (ConnectionManager conmgr)
    {
        // register ourselves as a connection observer
        conmgr.addConnectionObserver(this);
    }

    /**
     * Instructs the client manager to construct instances of this derived
     * class of <code>CherClient</code> to managed newly accepted client
     * connections.
     *
     * @see CherClient
     */
    public void setClientClass (Class clientClass)
    {
        // sanity check
        if (!CherClient.class.isAssignableFrom(clientClass)) {
            Log.warning("Requested to use client class that does not " +
                        "derive from CherClient " +
                        "[class=" + clientClass.getName() + "].");
            return;
        }

        // make a note of it
        _clientClass = clientClass;
    }

    /**
     * Instructs the client to create an instance of this
     * <code>ClientObject</code> derived class when creating the
     * distributed object that corresponds to a particular client session.
     *
     * @see com.threerings.cocktail.cher.data.ClientObject
     */
    public void setClientObjectClass (Class clobjClass)
    {
        // sanity check
        if (!ClientObject.class.isAssignableFrom(clobjClass)) {
            Log.warning("Requested to use client object class that does " +
                        "not derive from ClientObject " +
                        "[class=" + clobjClass.getName() + "].");
            return;
        }

        // make a note of it
        _clobjClass = clobjClass;
    }

    /**
     * Returns the class that should be used when creating a distributed
     * object to accompany a particular client session. In general, this
     * is only used by the <code>CherClient</code> object when it is
     * setting up a client's session for the first time.
     */
    public Class getClientObjectClass ()
    {
        return _clobjClass;
    }

    /**
     * Called when a new connection is established with the connection
     * manager. Only fully authenticated connections will be passed on to
     * the connection observer.
     *
     * @param conn The newly established connection.
     */
    public synchronized
        void connectionEstablished (Connection conn, Credentials creds)
    {
        String username = creds.getUsername();

        // see if there's a client already registered with this username
        CherClient client = (CherClient)_usermap.get(username);

        if (client != null) {
            Log.info("Session resumed [username=" + username +
                     ", conn=" + conn + "].");
            client.resumeSession(conn);

        } else {
            Log.info("Session initiated [username=" + username +
                     ", conn=" + conn + "].");
            // create a new client and stick'em in the table
            try {
                client = (CherClient)_clientClass.newInstance();
                client.startSession(this, username, conn);
                _usermap.put(username, client);
            } catch (Exception e) {
                Log.warning("Failed to instantiate client instance to " +
                            "manage new client connection " +
                            "[conn=" + conn + "].");
                Log.logStackTrace(e);
            }
        }

        // map this connection to this client
        _conmap.put(conn, client);
    }

    /**
     * Called if a connection fails for any reason. If a connection fails,
     * <code>connectionClosed</code> will not be called. This call to
     * <code>connectionFailed</code> is the last the observers will hear
     * about it.
     *
     * @param conn The connection in that failed.
     * @param fault The exception associated with the failure.
     */
    public synchronized
        void connectionFailed (Connection conn, IOException fault)
    {
        // remove the client from the connection map
        CherClient client = (CherClient)_conmap.remove(conn);
        if (client != null) {
            Log.info("Unmapped failed client [client=" + client +
                     ", conn=" + conn + ", fault=" + fault + "].");
            // let the client know the connection went away
            client.wasUnmapped();
            // and let the client know things went haywire
            client.connectionFailed(fault);

        } else {
            Log.info("Unmapped connection failed? [conn=" + conn +
                     ", fault=" + fault + "].");
        }
    }

    /**
     * Called when a connection has been closed in an orderly manner.
     *
     * @param conn The recently closed connection.
     */
    public synchronized void connectionClosed (Connection conn)
    {
        // remove the client from the connection map
        CherClient client = (CherClient)_conmap.remove(conn);
        if (client != null) {
            Log.info("Unmapped client [client=" + client +
                     ", conn=" + conn + "].");
            // let the client know the connection went away
            client.wasUnmapped();

        } else {
            Log.info("Closed unmapped connection? [conn=" + conn + "].");
        }
    }

    /**
     * Called by the client instance when the client requests a logoff.
     * This is called from the conmgr thread.
     */
    synchronized void clientDidEndSession (CherClient client)
    {
        // remove the client from the username map
        CherClient rc = (CherClient)_usermap.remove(client.getUsername());

        // sanity check because we can
        if (rc == null) {
            Log.warning("Unregistered client ended session " +
                        "[client=" + client + "].");

        } else if (rc != client) {
            Log.warning("Different clients with same username!? " +
                        "[c1=" + rc + ", c2=" + client + "].");

        } else {
            Log.info("Ending session [client=" + client + "].");
        }
    }

    protected HashMap _usermap = new HashMap();
    protected HashMap _conmap = new HashMap();

    protected Class _clientClass = CherClient.class;
    protected Class _clobjClass = ClientObject.class;
}
