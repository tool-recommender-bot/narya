//
// $Id: Pathable.java,v 1.1 2002/05/31 07:34:45 mdb Exp $

package com.threerings.media.util;

import com.threerings.util.DirectionCodes;

/**
 * Used in conjunction with a {@link Path}.
 */
public interface Pathable
{
    /**
     * Returns the pathable's current x coordinate.
     */
    public int getX ();

    /**
     * Returns the pathable's current y coordinate.
     */
    public int getY ();

    /**
     * Updates the pathable's current coordinates.
     */
    public void setLocation (int x, int y);

    /**
     * Will be called by a path when it moves the pathable in the
     * specified direction. Pathables that wish to face in the direction
     * they are moving can take advantage of this callback.
     *
     * @see DirectionCodes
     */
    public void setOrientation (int orient);

    /**
     * Called by a path when this pathable is made to start along a path.
     */
    public void pathBeginning ();

    /**
     * Called by a path when this pathable finishes moving along its path.
     */
    public void pathCompleted ();
}
