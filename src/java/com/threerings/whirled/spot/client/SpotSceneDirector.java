//
// $Id: SpotSceneDirector.java,v 1.7 2001/12/16 21:47:37 mdb Exp $

package com.threerings.whirled.spot.client;

import java.util.Iterator;
import com.samskivert.util.StringUtil;

import com.threerings.presents.dobj.DObject;
import com.threerings.presents.dobj.DObjectManager;
import com.threerings.presents.dobj.ObjectAccessException;
import com.threerings.presents.dobj.Subscriber;

import com.threerings.crowd.chat.ChatDirector;
import com.threerings.crowd.client.LocationAdapter;
import com.threerings.crowd.client.LocationDirector;
import com.threerings.crowd.data.PlaceObject;

import com.threerings.whirled.client.SceneDirector;
import com.threerings.whirled.data.SceneModel;
import com.threerings.whirled.util.WhirledContext;

import com.threerings.whirled.spot.Log;
import com.threerings.whirled.spot.data.Location;
import com.threerings.whirled.spot.data.Portal;

/**
 * Extends the standard scene director with facilities to move between
 * locations within a scene.
 */
public class SpotSceneDirector
    implements SpotCodes, Subscriber
{
    /**
     * This is used to communicate back to the caller of {@link
     * #changeLocation}.
     */
    public static interface ChangeObserver
    {
        /**
         * Indicates that the requested location change succeeded.
         */
        public void locationChangeSucceeded (int locationId);

        /**
         * Indicates that the requested location change failed and
         * provides a reason code explaining the failure.
         */
        public void locationChangeFailed (int locationId, String reason);
    }

    /**
     * Creates a new spot scene director with the specified context and
     * which will cooperate with the supplied scene director.
     *
     * @param ctx the active client context.
     * @param locdir the location director with which we will be
     * cooperating.
     * @param scdir the scene director with which we will be cooperating.
     */
    public SpotSceneDirector (WhirledContext ctx, LocationDirector locdir,
                              SceneDirector scdir)
    {
        _ctx = ctx;
        _scdir = scdir;

        // wire ourselves up to hear about leave place notifications
        locdir.addLocationObserver(new LocationAdapter() {
            public void locationDidChange (PlaceObject place) {
                // we need to clear out our cluster chat object
                // subscriptions when we leave a place
                clearClusterScrips();
            }
        });
    }

    /**
     * Configures this spot scene director with a chat director, with
     * which it will coordinate to implement cluster chatting.
     */
    public void setChatDirector (ChatDirector chatdir)
    {
        _chatdir = chatdir;
    }

    /**
     * Requests that this client move to the location specified by the
     * supplied portal id. A request will be made and when the response is
     * received, the location observers will be notified of success or
     * failure.
     */
    public void traversePortal (int portalId)
    {
        // look up the destination scene and location
        DisplaySpotScene scene = (DisplaySpotScene)_scdir.getScene();
        if (scene == null) {
            Log.warning("Requested to traverse portal when we have " +
                        "no scene [portalId=" + portalId + "].");
            return;
        }

        // find the portal they're talking about
        int targetSceneId = -1, targetLocId = -1;
        Iterator portals = scene.getPortals().iterator();
        while (portals.hasNext()) {
            Portal portal = (Portal)portals.next();
            if (portal.locationId == portalId) {
                targetSceneId = portal.targetSceneId;
                targetLocId = portal.targetLocId;
            }
        }

        // make sure we found the portal
        if (targetSceneId == -1) {
            portals = scene.getPortals().iterator();
            Log.warning("Requested to traverse non-existent portal " +
                        "[portalId=" + portalId +
                        ", portals=" + StringUtil.toString(portals) + "].");
        }

        // prepare to move to this scene (sets up pending data)
        if (!_scdir.prepareMoveTo(targetSceneId)) {
            return;
        }

        // check the version of our cached copy of the scene to which
        // we're requesting to move; if we were unable to load it, assume
        // a cached version of zero
        int sceneVer = 0;
        SceneModel pendingModel = _scdir.getPendingModel();
        if (pendingModel != null) {
            sceneVer = pendingModel.version;
        }

        // issue a traversePortal request
        SpotService.traversePortal(
            _ctx.getClient(), scene.getId(), portalId, sceneVer, _scdir);
    }

    /**
     * Issues a request to change our location within the scene to the
     * location identified by the specified id. Most client entities find
     * out about location changes via changes to the occupant info data,
     * but the initiator of a location change request can be notified of
     * its success or failure, primarily so that it can act in
     * anticipation of a successful location change (like by starting a
     * sprite moving toward the new location), but backtrack if it finds
     * out that the location change failed.
     */
    public void changeLocation (int locationId, ChangeObserver obs)
    {
        // refuse if there's a pending location change
        if (_pendingLocId != -1) {
            return;
        }

        // make sure we're currently in a scene
        DisplaySpotScene scene = (DisplaySpotScene)_scdir.getScene();
        if (scene == null) {
            Log.warning("Requested to change locations, but we're not " +
                        "currently in any scene [locId=" + locationId + "].");
            return;
        }

        // make sure the specified location is in the current scene
        Location loc = scene.getLocation(locationId);
        if (loc == null) {
            Log.warning("Requested to change to a location that's not " +
                        "in the current scene [locs=" + StringUtil.toString(
                            scene.getLocations().iterator()) +
                        ", locId=" + locationId + "].");
            return;
        }

        // make a note that we're changing to this location
        _pendingLocId = locationId;
        _changeObserver = obs;
        // and send the location change request
        SpotService.changeLoc(_ctx.getClient(), scene.getId(),
                              locationId, this);
    }

    /**
     * Sends a chat message to the other users in the cluster to which the
     * location that we currently occupy belongs.
     */
    public void requestClusterSpeak (String message)
    {
        // make sure we're currently in a scene
        DisplaySpotScene scene = (DisplaySpotScene)_scdir.getScene();
        if (scene == null) {
            Log.warning("Requested to speak to cluster, but we're not " +
                        "currently in any scene [message=" + message + "].");
            return;
        }

        // make sure we're in a location
        Location loc = scene.getLocation(_locationId);
        if (loc == null) {
            Log.info("Ignoring cluster speak as we're not in a valid " +
                     "location [locId=" + _locationId + "].");
            return;
        }

        // make sure the location has an associated cluster
        if (loc.clusterIndex == -1) {
            Log.info("Ignoring cluster speak as our location has no " +
                     "cluster [loc=" + loc + "].");
            return;
        }

        // we're all clear to go
        SpotService.clusterSpeak(
            _ctx.getClient(), scene.getId(), _locationId, message, this);
    }

    /**
     * Called in response to a successful <code>changeLoc</code> request.
     */
    public void handleChangeLocSucceeded (int invid, int clusterOid)
    {
        ChangeObserver obs = _changeObserver;
        _locationId = _pendingLocId;

        // clear out our pending location info
        _pendingLocId = -1;
        _changeObserver = null;

        // determine if our cluster oid changed (which we only care about
        // if we're doing cluster chat)
        if (_chatdir != null) {
            int oldOid = (_clobj == null) ? -1 : _clobj.getOid();
            if (clusterOid != oldOid) {
                DObjectManager omgr = _ctx.getDObjectManager();
                // remove our old subscription if necessary
                if (_clobj != null) {
                    _chatdir.removeAuxilliarySource(_clobj);
                    // unsubscribe from our old object
                    omgr.unsubscribeFromObject(_clobj.getOid(), this);
                    _clobj = null;
                }
                // create a new subscription (we'll wire it up to the chat
                // director when the subscription completes
                omgr.subscribeToObject(clusterOid, this);
            }
        }

        // if we had an observer, let them know things went well
        if (obs != null) {
            obs.locationChangeSucceeded(_locationId);
        }
    }

    /**
     * Called in response to a failed <code>changeLoc</code> request.
     */
    public void handleChangeLocFailed (int invid, String reason)
    {
        ChangeObserver obs = _changeObserver;
        int locId = _pendingLocId;

        // clear out our pending location info
        _pendingLocId = -1;
        _changeObserver = null;

        // if we had an observer, let them know things went well
        if (obs != null) {
            obs.locationChangeFailed(locId, reason);
        }
    }

    // documentation inherited
    public void objectAvailable (DObject object)
    {
        // we've got our cluster chat object, configure the chat director
        // with it and keep a reference ourselves
        if (_chatdir != null) {
            _chatdir.addAuxilliarySource(CLUSTER_CHAT_TYPE, object);
            _clobj = object;
        }
    }

    // documentation inherited
    public void requestFailed (int oid, ObjectAccessException cause)
    {
        Log.warning("Unable to subscribe to cluster chat object " +
                    "[oid=" + oid + ",, cause=" + cause + "].");
    }

    /**
     * Clears out any cluster subscriptions we may have.
     */
    protected void clearClusterScrips ()
    {
        if (_chatdir != null && _clobj != null) {
            // unwire the auxilliary chat object
            _chatdir.removeAuxilliarySource(_clobj);
            // unsubscribe as well
            DObjectManager omgr = _ctx.getDObjectManager();
            omgr.unsubscribeFromObject(_clobj.getOid(), this);
            _clobj = null;
        }
    }

    /** The active client context. */
    protected WhirledContext _ctx;

    /** The scene director with which we are cooperating. */
    protected SceneDirector _scdir;

    /** A reference to the chat director with which we coordinate. */
    protected ChatDirector _chatdir;

    /** The location id of the location we currently occupy. */
    protected int _locationId = -1;

    /** The location id on which we have an outstanding change location
     * request. */
    protected int _pendingLocId = -1;

    /** The cluster chat object for the cluster we currently occupy. */
    protected DObject _clobj;

    /** An entity that wants to know if a requested location change
     * succeded or failed. */
    protected ChangeObserver _changeObserver;
}
