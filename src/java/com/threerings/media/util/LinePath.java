//
// $Id: LinePath.java,v 1.9 2002/06/18 22:25:33 mdb Exp $

package com.threerings.media.util;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.Point;

import com.samskivert.util.StringUtil;

import com.threerings.media.Log;
import com.threerings.media.util.MathUtil;

/**
 * The line path is used to cause a pathable to go from point A to point B
 * in a certain number of milliseconds.
 */
public class LinePath implements Path
{
    /**
     * Constructs a line path between the two specified points that will
     * be followed in the specified number of milliseconds.
     */
    public LinePath (int x1, int y1, int x2, int y2, long duration)
    {
        this(new Point(x1, y1), new Point(x2, y2), duration);
    }

    /**
     * Constructs a line path between the two specified points that will
     * be followed in the specified number of milliseconds.
     */
    public LinePath (Point source, Point dest, long duration)
    {
        // sanity check some things
        if (duration <= 0) {
            String errmsg = "Requested path with illegal duration (<=0) " +
                "[duration=" + duration + "]";
            throw new IllegalArgumentException(errmsg);
        }

        _source = source;
        _dest = dest;
        _duration = duration;
    }

    // documentation inherited
    public void init (Pathable pable, long timestamp)
    {
        // give the pable a chance to perform any starting antics
        pable.pathBeginning();

        // make a note of when we started
        _startStamp = timestamp;

        // update our position to the start of the path
        tick(pable, timestamp);
    }

    // documentation inherited
    public boolean tick (Pathable pable, long timestamp)
    {
        // if we've blown past our arrival time, we need to get our bootay
        // to the prearranged spot and get the hell out
        if (timestamp >= _startStamp + _duration) {
            pable.setLocation(_dest.x, _dest.y);
            pable.pathCompleted();
            return true;
        }

        // determine where we should be along the path
        long elapsed = timestamp - _startStamp;
        computePosition(_source, _dest, elapsed, _duration, _tpos);

        // only update the pathable's location if it actually moved
        if (pable.getX() != _tpos.x || pable.getY() != _tpos.y) {
            pable.setLocation(_tpos.x, _tpos.y);
            return true;
        }

        return false;
    }

    // documentation inherited
    public void fastForward (long timeDelta)
    {
        _startStamp += timeDelta;
    }

    // documentation inherited
    public void paint (Graphics2D gfx)
    {
	gfx.setColor(Color.red);
        gfx.drawLine(_source.x, _source.y, _dest.x, _dest.y);
    }

    // documentation inherited
    public String toString ()
    {
        return "[src=" + StringUtil.toString(_source) +
            ", dest=" + StringUtil.toString(_dest) +
            ", duration=" + _duration + "ms]";
    }

    /**
     * Computes the position of an entity along the path defined by the
     * supplied start and end points assuming that it must finish the path
     * in the specified duration (in millis) and has been traveling the
     * path for the specified number of elapsed milliseconds.
     */
    public static void computePosition (Point start, Point end, long elapsed,
                                        long duration, Point pos)
    {
        float pct = (float)elapsed / duration;
        int travx = (int)((end.x - start.x) * pct);
        int travy = (int)((end.y - start.y) * pct);
        pos.setLocation(start.x + travx, start.y + travy);
    }

    /** Our source and destination points. */
    protected Point _source, _dest;

    /** A temporary point used when computing our position along the
     * path. */
    protected Point _tpos = new Point();

    /** The time at which we started along the path. */
    protected long _startStamp;

    /** The duration that we're to spend following the path. */
    protected long _duration;
}
