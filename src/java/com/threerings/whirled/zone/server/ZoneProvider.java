//
// $Id: ZoneProvider.java,v 1.11 2002/09/20 00:54:06 mdb Exp $

package com.threerings.whirled.zone.server;

import com.threerings.presents.data.ClientObject;
import com.threerings.presents.server.InvocationException;
import com.threerings.presents.server.InvocationProvider;

import com.threerings.crowd.data.BodyObject;
import com.threerings.crowd.data.PlaceConfig;
import com.threerings.crowd.data.PlaceObject;
import com.threerings.crowd.server.LocationProvider;

import com.threerings.whirled.data.SceneModel;
import com.threerings.whirled.data.ScenedBodyObject;
import com.threerings.whirled.server.SceneManager;
import com.threerings.whirled.server.SceneRegistry;

import com.threerings.whirled.zone.Log;
import com.threerings.whirled.zone.client.ZoneService.ZoneMoveListener;
import com.threerings.whirled.zone.data.ZoneCodes;
import com.threerings.whirled.zone.data.ZoneSummary;
import com.threerings.whirled.zone.data.ZonedBodyObject;

/**
 * Provides zone related services which are presently the ability to move
 * from zone to zone.
 */
public class ZoneProvider
    implements ZoneCodes, InvocationProvider
{
    /**
     * Constructs a zone provider that will interoperate with the supplied
     * zone and scene registries. The zone provider will automatically be
     * constructed and registered by the {@link ZoneRegistry}, which a
     * zone-using system must create and initialize in their server.
     */
    public ZoneProvider (LocationProvider locprov, ZoneRegistry zonereg,
                         SceneRegistry screg)
    {
        _locprov = locprov;
        _zonereg = zonereg;
        _screg = screg;
    }

    /**
     * Processes a request from a client to move to a scene in a new zone.
     *
     * @param caller the user requesting the move.
     * @param invid the invocation id of the request.
     * @param zoneId the qualified zone id of the new zone.
     * @param sceneId the identifier of the new scene.
     * @param sceneVer the version of the scene model currently held by
     * the client.
     * @param listener the entity to inform of success or failure.
     */
    public void moveTo (ClientObject caller, int zoneId, int sceneId,
                        int sceneVer, ZoneMoveListener listener)
        throws InvocationException
    {
        // avoid cluttering up the method declaration with final keywords
        final BodyObject fsource = (BodyObject)caller;
        final int fsceneId = sceneId;
        final int fsceneVer = sceneVer;
        final ZoneMoveListener flistener = listener;

        // look up the zone manager for the zone
        ZoneManager zmgr = _zonereg.getZoneManager(zoneId);
        if (zmgr == null) {
            Log.warning("Requested to enter a zone for which we have no " +
                        "manager [user=" + fsource.who() +
                        ", zoneId=" + zoneId + "].");
            throw new InvocationException(NO_SUCH_ZONE);
        }

        // resolve the zone!
        ZoneManager.ResolutionListener zl = new ZoneManager.ResolutionListener()
        {
            public void zoneWasResolved (ZoneSummary summary) {
                continueMoveTo(
                    fsource, summary, fsceneId, fsceneVer, flistener);
            }

            public void zoneFailedToResolve (int zoneId, Exception reason) {
                Log.warning("Unable to resolve zone [zoneId=" + zoneId +
                            ", reason=" + reason + "].");
                flistener.requestFailed(NO_SUCH_ZONE);
            }
        };
        zmgr.resolveZone(zoneId, zl);
    }

    /**
     * This is called after we have resolved our zone.
     */
    protected void continueMoveTo (
        BodyObject source, ZoneSummary summary, int sceneId, int sceneVer,
        ZoneMoveListener listener)
    {
        // avoid cluttering up the method declaration with final keywords
        final BodyObject fsource = source;
        final ZoneSummary fsum = summary;
        final int fsceneVer = sceneVer;
        final ZoneMoveListener flistener = listener;

        // give the zone manager a chance to veto the request
        ZoneManager zmgr = _zonereg.getZoneManager(summary.zoneId);
        String errmsg = zmgr.ratifyBodyEntry(source, summary.zoneId);
        if (errmsg != null) {
            listener.requestFailed(errmsg);
            return;
        }

        // create a callback object that will handle the resolution or
        // failed resolution of the scene
        SceneRegistry.ResolutionListener rl =
            new SceneRegistry.ResolutionListener()
        {
            public void sceneWasResolved (SceneManager scmgr) {
                finishMoveTo(fsource, fsum, scmgr, fsceneVer, flistener);
            }

            public void sceneFailedToResolve (int sceneId, Exception reason) {
                Log.warning("Unable to resolve scene [sceneid=" + sceneId +
                            ", reason=" + reason + "].");
                // pretend like the scene doesn't exist to the client
                flistener.requestFailed(NO_SUCH_PLACE);
            }
        };

        // make sure the scene they are headed to is actually loaded into
        // the server
        _screg.resolveScene(sceneId, rl);
    }

    /**
     * This is called after the scene to which we are moving is guaranteed
     * to have been loaded into the server.
     */
    protected void finishMoveTo (
        BodyObject source, ZoneSummary summary, SceneManager scmgr,
        int sceneVersion, ZoneMoveListener listener)
    {
        // move to the place object associated with this scene
        PlaceObject plobj = scmgr.getPlaceObject();
        int ploid = plobj.getOid();

        try {
            // try doing the actual move
            PlaceConfig config = _locprov.moveTo(source, ploid);

            // now that we've finally moved, we can update the user object
            // with the new scene and zone ids
            try {
                source.startTransaction();
                ((ScenedBodyObject)source).setSceneId(scmgr.getScene().getId());
                ((ZonedBodyObject)source).setZoneId(summary.zoneId);
            } finally {
                source.commitTransaction();
            }

            // check to see if they need a newer version of the scene data
            SceneModel model = scmgr.getSceneModel();
            if (sceneVersion < model.version) {
                // then send the moveTo response
                listener.moveSucceededPlusUpdate(ploid, config, summary, model);

            } else {
                // then send the moveTo response
                listener.moveSucceeded(ploid, config, summary);
            }

            // let the zone manager know that someone just came on in
            ZoneManager zmgr = _zonereg.getZoneManager(summary.zoneId);
            zmgr.bodyDidEnterZone(source, summary.zoneId);

        } catch (InvocationException ie) {
            listener.requestFailed(ie.getMessage());
        }
    }

    /**
     * Ejects the specified body from their current scene and sends them a
     * request to move to the specified new zone and scene. This is the
     * zone-equivalent to {@link LocationProvider#moveBody}.
     */
    public void moveBody (BodyObject source, int zoneId, int sceneId)
    {
        // first remove them from their old place
        _locprov.leaveOccupiedPlace(source);

        // then send a forced move notification
        ZoneSender.forcedMove(source, zoneId, sceneId);
    }

    /** The entity that handles basic location changes. */
    protected LocationProvider _locprov;

    /** The zone registry with which we communicate. */
    protected ZoneRegistry _zonereg;

    /** The scene registry with which we communicate. */
    protected SceneRegistry _screg;
}
