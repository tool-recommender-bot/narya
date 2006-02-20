//
// $Id: ClientDObjectMgr.java 3795 2005-12-21 19:30:39Z mdb $
//
// Narya library - tools for developing networked games
// Copyright (C) 2002-2004 Three Rings Design, Inc., All Rights Reserved
// http://www.threerings.net/code/narya/
//
// This library is free software; you can redistribute it and/or modify it
// under the terms of the GNU Lesser General Public License as published
// by the Free Software Foundation; either version 2.1 of the License, or
// (at your option) any later version.
//
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
// Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public
// License along with this library; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA

package com.threerings.presents.client {

import flash.events.TimerEvent;
import flash.util.Timer;

import mx.collections.IList;

import com.threerings.util.SimpleMap;

import com.threerings.presents.dobj.DObject;
import com.threerings.presents.dobj.DObjectManager;

/**
 * The client distributed object manager manages a set of proxy objects
 * which mirror the distributed objects maintained on the server.
 * Requests for modifications, etc. are forwarded to the server and events
 * are dispatched from the server to this client for objects to which this
 * client is subscribed.
 */
public class ClientDObjectMgr
    implements DObjectManager
{
    /**
     * Constructs a client distributed object manager.
     *
     * @param comm a communicator instance by which it can communicate
     * with the server.
     * @param client a reference to the client that is managing this whole
     * communications and event dispatch business.
     */
    public function ClientDObjectMgr (comm :Communicator, client :Client)
    {
        _comm = comm;
        _client = client;

        // register a flush interval
        _flushInterval = new Timer(FLUSH_INTERVAL);
        _flushInterval.addEventListener(TimerEvent.TIMER, flushObjects);
        _flushInterval.start();

        _actionInterval = new Timer(1); //TODO!
        _actionInterval.addEventListener(TimerEvent.TIMER, processNextAction);
        _actionInterval.start();
    }

    // documentation inherited from interface DObjectManager
    public function isManager (object :DObject) :Boolean
    {
        // we are never authoritative in the present implementation
        return false;
    }

    // inherit documentation from the interface DObjectManager
    public function createObject (dclass :Class, target :Subscriber) :void
    {
        // not presently supported
        throw new Error("createObject() not supported");
    }

    // inherit documentation from the interface DObjectManager
    public function subscribeToObject (oid :int, target :Subscriber) :void
    {
        if (oid <= 0) {
            target.requestFailed(
                oid, new ObjectAccessError("Invalid oid " + oid + "."));
        } else {
            queueAction(oid, target, true);
        }
    }

    // inherit documentation from the interface DObjectManager
    public function unsubscribeFromObject (oid :int, target :Subscriber) :void
    {
        queueAction(oid, target, false);
    }

    protected function queueAction (
            oid :int, target :Subscriber, subscribe :Boolean) :void
    {
        // queue up an action
        _actions.push(new ObjectAction(oid, target, subscribe));
    }

    // inherit documentation from the interface
    public function postEvent (event :DEvent) :void
    {
        // send a forward event request to the server
        _comm.postMessage(new ForwardEventRequest(event));
    }

    // inherit documentation from the interface
    public function destroyObject (oid :int) :void
    {
        // forward an object destroyed event to the server
        postEvent(new ObjectDestroyedEvent(oid));
    }

    // inherit documentation from the interface
    public function removedLastSubscriber (
            obj :DObject, deathWish :Boolean) :void
    {
        // if this object has a registered flush delay, don't can it just
        // yet, just slip it onto the flush queue
        var oclass :Class = ClassUtil.getClass(obj);
        /*
        // TODO
        // TODO
        for (Iterator iter = _delays.keySet().iterator(); iter.hasNext(); ) {
            Class dclass = (Class)iter.next();
            if (dclass.isAssignableFrom(oclass)) {
                long expire =  System.currentTimeMillis() +
                    ((Long)_delays.get(dclass)).longValue();
                _flushes.put(obj.getOid(), new FlushRecord(obj, expire));
//                 Log.info("Flushing " + obj.getOid() + " at " +
//                          new java.util.Date(expire));
                return;
            }
        }
        */

        // if we didn't find a delay registration, flush immediately
        flushObject(obj);
    }

    /**
     * Registers an object flush delay.
     *
     * @see Client#registerFlushDelay
     */
    public function registerFlushDelay (objclass :Class, delay :Number) :void
    {
        // TODO
        //_delays.put(objclass, new Long(delay));
    }

    /**
     * Called by the communicator when a downstream message arrives from
     * the network layer. We queue it up for processing and request some
     * processing time on the main thread.
     */
    public function processMessage (msg :DownstreamMessage) :void
    {
        // append it to our queue
        _actions.push(msg);
    }

    /**
     * Invoked on the main client thread to process any newly arrived
     * messages that we have waiting in our queue.
     */
    public function processNextAction (event :TimerEvent) :void
    {
        // process the next event on our queue
        if (_actions.length == 0) {
            return;
        }

        var obj :Object = _actions.shift();
        // do the proper thing depending on the object
        if (obj is BootstrapNotification) {
            _client.gotBootstrap(obj.getData(), this);

        } else if (obj is EventNotification) {
            var evt :DEvent = obj.getEvent();
//                 Log.info("Dispatch event: " + evt);
            dispatchEvent(evt);

        } else if (obj is ObjectResponse) {
            registerObjectAndNotify(obj.getObject());

        } else if (obj is UnsubscribeResponse) {
            var oid :int = obj.getOid();
            if (_dead[oid] == null) {
                trace("Received unsub ACK from unknown object " +
                            "[oid=" + oid + "].");
            }
            _dead[oid] = undefined;

        } else if (obj is FailureResponse) {
            var oid :int = obj.getOid();
            notifyFailure(oid);

        } else if (obj is PongResponse) {
            _client.gotPong(obj);

        } else if (obj is ObjectAction) {
            var act :ObjectAction = (obj as ObjectAction);
            if (act.subscribe) {
                doSubscribe(act.oid, act.target);
            } else {
                doUnsubscribe(act.oid, act.target);
            }
        }
    }

    /**
     * Called when a new event arrives from the server that should be
     * dispatched to subscribers here on the client.
     */
    protected function dispatchEvent (event :DEvent) :void
    {
        // if this is a compound event, we need to process its contained
        // events in order
        if (event is CompoundEvent) {
            var events :IList = event.getEvents();
            var ecount :int = events.length;
            for (var ii :int = 0; ii < ecount; ii++) {
                dispatchEvent(events.getItemAt(ii));
            }
            return;
        }

        // look up the object on which we're dispatching this event
        var toid :int = event.getTargetOid();
        var target :DObject = _ocache[toid];
        if (target == null) {
            if (_dead[toid] === undefined) {
                trace("Unable to dispatch event on non-proxied " +
                      "object [event=" + event + "].");
            }
            return;
        }

        try {
            // apply the event to the object
            var notify :Boolean = event.applyToObject(target);

            // if this is an object destroyed event, we need to remove the
            // object from our object table
            if (event is ObjectDestroyedEvent) {
//                 Log.info("Pitching destroyed object " +
//                          "[oid=" + toid + ", class=" +
//                          StringUtil.shortClassName(target) + "].");
                _ocache[toid] = undefined;
            }

            // have the object pass this event on to its listeners
            if (notify) {
                target.notifyListeners(event);
            }

        } catch (e :Exception) {
            trace("Failure processing event [event=" + event +
                ", target=" + target + "].");
            //Log.logStackTrace(e);
        }
    }

    /**
     * Registers this object in our proxy cache and notifies the
     * subscribers that were waiting for subscription to this object.
     */
    protected function registerObjectAndNotify (obj :DObject) :void
    {
        // let the object know that we'll be managing it
        obj.setManager(this);

        var oid :int = obj.getOid();
        // stick the object into the proxy object table
        _ocache[oid] = obj;

        // let the penders know that the object is available
        var req :PendingRequest = _penders[oid];
        _penders[oid] = undefined;
        if (req == null) {
            trace("Got object, but no one cares?! " +
                  "[oid=" + oid + ", obj=" + obj + "].");
            return;
        }

        for (var ii :int  = 0; ii < req.targets.length; ii++) {
            var target :Subscriber = req.targets[ii];
            // add them as a subscriber
            obj.addSubscriber(target);
            // and let them know that the object is in
            target.objectAvailable(obj);
        }
    }

    /**
     * Notifies the subscribers that had requested this object (for
     * subscription) that it is not available.
     */
    protected function notifyFailure (oid :int) :void
    {
        // let the penders know that the object is not available
        var req :PendingRequest = _penders[oid];
        _penders[oid] = undefined;
        if (req == null) {
            trace("Failed to get object, but no one cares?! " +
                        "[oid=" + oid + "].");
            return;
        }

        for (var ii :int = 0; ii < req.targets.length; ii++) {
            var target :Subscriber = req.targets[ii];
            // and let them know that the object is in
            target.requestFailed(oid, null);
        }
    }

    /**
     * This is guaranteed to be invoked via the invoker and can safely do
     * main thread type things like call back to the subscriber.
     */
    protected function doSubscribe (oid :int, target :Subscriber) :void
    {
        // Log.info("doSubscribe: " + oid + ": " + target);

        // first see if we've already got the object in our table
        var obj :DObject = _ocache[oid];
        if (obj != null) {
            // clear the object out of the flush table if it's in there
            _flushes[oid] = undefined;
            // add the subscriber and call them back straight away
            obj.addSubscriber(target);
            target.objectAvailable(obj);
            return;
        }

        // see if we've already got an outstanding request for this object
        var req :PendingRequest = _penders[oid];
        if (req != null) {
            // add this subscriber to the list of subscribers to be
            // notified when the request is satisfied
            req.addTarget(target);
            return;
        }

        // otherwise we need to create a new request
        req = new PendingRequest(oid);
        req.addTarget(target);
        _penders.put(oid, req);
        // Log.info("Registering pending request [oid=" + oid + "].");

        // and issue a request to get things rolling
        _comm.postMessage(new SubscribeRequest(oid));
    }

    /**
     * This is guaranteed to be invoked via the invoker and can safely do
     * main thread type things like call back to the subscriber.
     */
    protected function doUnsubscribe (oid :int, target :Subscriber) :void
    {
        var dobj :DObject = _ocache[oid];
        if (dobj != null) {
            dobj.removeSubscriber(target);

        } else {
            trace("Requested to remove subscriber from " +
                     "non-proxied object [oid=" + oid +
                     ", sub=" + target + "].");
        }
    }

    /**
     * Flushes a distributed object subscription, issuing an unsubscribe
     * request to the server.
     */
    protected function flushObject (obj :DObject) :void
    {
        // move this object into the dead pool so that we don't claim to
        // have it around anymore; once our unsubscribe message is
        // processed, it'll be 86ed
        var ooid :int = obj.getOid();
        _ocache[ooid] = undefined;
        _dead[ooid] = obj;

        // ship off an unsubscribe message to the server; we'll remove the
        // object from our table when we get the unsub ack
        _comm.postMessage(new UnsubscribeRequest(ooid));
    }

    /**
     * Called periodically to flush any objects that have been lingering
     * due to a previously enacted flush delay.
     */
    protected function flushObjects () :void
    {
        var now :Number = new Date().getTime();
        for (var oid :int in _flushes) {
            var rec :FlushRecord = _flushes[oid];
            if (rec.expire <= now) {
                _flushes[oid] = undefined;
                flushObject(rec.object);
            }
        }
    }

    /** A reference to the communicator that sends and receives messages
     * for this client. */
    protected var _comm :Communicator;

    /** A reference to our client instance. */
    protected var _client :Client;

    /** Our primary dispatch queue. */
    protected var _actions :Array = new Array();

    /** All of the distributed objects that are active on this client. */
    protected var _ocache :SimpleMap = new SimpleMap(); //HashIntMap();

    /** Objects that have been marked for death. */
    protected var _dead :SimpleMap = new SimpleMap(); //HashIntMap();

    /** Pending object subscriptions. */
    protected var _penders :SimpleMap = new SimpleMap(); //HashIntMap();

    /** A mapping from distributed object class to flush delay. */
    protected var _delays :SimpleMap = new SimpleMap(); //HashMap();

    /** A set of objects waiting to be flushed. */
    protected var _flushes :SimpleMap = new SimpleMap(); //HashIntMap();

    /** Flushes objects every now and again. */
    protected var _flushInterval :Timer;
    protected var _flushInterval :Timer;

    /** Flush expired objects every 30 seconds. */
    protected static const FLUSH_INTERVAL :Number = 30 * 1000;
}
}

/**
 * The object action is used to queue up a subscribe or unsubscribe
 * request.
 */
protected class ObjectAction
{
    public var oid :int;
    public var target :Subscriber;
    public var subscribe :Boolean;

    public function ObjectAction (
            oid :int, target :Subscriber, subscribe :Boolean)
    {
        this.oid = oid;
        this.target = target;
        this.subscribe = subscribe;
    }

    public override function toString () :String
    {
        return "oid=" + oid + ", target=" + target + ", subscribe=" + subscribe;
    }
}

protected class PendingRequest
{
    public var oid :int;
    public var targets :Array = new Array();

    public function PendingRequest (oid :int)
    {
        this.oid = oid;
    }

    public function addTarget (target :Subscriber) :void
    {
        targets.push(target);
    }
}

/** Used to manage pending object flushes. */
protected class FlushRecord
{
    /** The object to be flushed. */
    public var obj :DObject;

    /** The time at which we flush it. */
    public var expire :Number;

    public function FlushRecord (obj :DObject, expire :Number)
    {
        this.object = object;
        this.expire = expire;
    }
}