//
// $Id: CrowdClient.java,v 1.17 2002/11/26 02:46:01 mdb Exp $

package com.threerings.crowd.server;

import com.threerings.presents.server.PresentsClient;

import com.threerings.crowd.Log;
import com.threerings.crowd.data.BodyObject;
import com.threerings.crowd.data.OccupantInfo;
import com.threerings.crowd.data.PlaceObject;

/**
 * The crowd client extends the presents client with crowd-specific client
 * handling.
 */
public class CrowdClient extends PresentsClient
{
    // documentation inherited
    protected void sessionConnectionClosed ()
    {
        super.sessionConnectionClosed();

        if (_clobj != null) {
            // note that the user's disconnected
            BodyObject bobj = (BodyObject)_clobj;
            BodyProvider.updateOccupantStatus(
                bobj, bobj.location, OccupantInfo.DISCONNECTED);
        }
    }

    // documentation inherited
    protected void sessionWillResume ()
    {
        super.sessionWillResume();

        // note that the user's active once more
        BodyObject bobj = (BodyObject)_clobj;
        BodyProvider.updateOccupantStatus(
            bobj, bobj.location, OccupantInfo.ACTIVE);
    }

    // documentation inherited
    protected void sessionDidEnd ()
    {
        // clear out our location so that anyone listening for such things
        // will know that we've left
        if (_clobj != null) {
            BodyObject bobj = (BodyObject)_clobj;
            bobj.setLocation(-1);
        }

        super.sessionDidEnd();
    }
}
