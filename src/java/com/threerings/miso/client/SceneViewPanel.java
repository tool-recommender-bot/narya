//
// $Id: SceneViewPanel.java,v 1.23 2002/01/11 16:17:34 shaper Exp $

package com.threerings.miso.scene;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Rectangle;

import java.util.List;

import com.samskivert.util.Config;

import com.threerings.media.animation.AnimationManager;
import com.threerings.media.animation.AnimatedPanel;
import com.threerings.media.sprite.SpriteManager;
import com.threerings.miso.util.MisoUtil;

/**
 * The scene view panel is responsible for managing a {@link
 * SceneView}, rendering it to the screen, and handling view-related
 * UI events.
 */
public class SceneViewPanel extends AnimatedPanel
    implements IsoSceneViewModelListener
{
    /**
     * Constructs the scene view panel.
     */
    public SceneViewPanel (Config config, SpriteManager spritemgr)
    {
        // save off references
        _spritemgr = spritemgr;

        // create an animation manager for this panel
        _animmgr = new AnimationManager(_spritemgr, this);

        // create the data model for the scene view
        _viewmodel = new IsoSceneViewModel(config);

        // listen to the iso scene view model to receive notice when
        // the scene display has changed and needs must be repainted
        _viewmodel.addListener(this);

	// create the scene view
        _view = newSceneView(_animmgr, spritemgr, _viewmodel);
    }

    /**
     * Gets the iso scene view model associated with this panel.
     */
    public IsoSceneViewModel getModel ()
    {
        return _viewmodel;
    }

    /**
     * Constructs the underlying scene view implementation.
     */
    protected IsoSceneView newSceneView (
	AnimationManager amgr, SpriteManager smgr, IsoSceneViewModel model)
    {
        return new IsoSceneView(amgr, smgr, model);
    }

    /**
     * Sets the scene managed by the panel.
     */
    public void setScene (DisplayMisoScene scene)
    {
	_view.setScene(scene);
    }

    /**
     * Gets the scene managed by the panel.
     */
    public SceneView getSceneView ()
    {
	return _view;
    }

    // documentation inherited
    protected void render (Graphics g)
    {
        _view.paint(g);
    }

    // documentation inherited
    public void invalidateRects (List rects)
    {
        // pass the invalid rects on to our scene view
        _view.invalidateRects(rects);
    }

    // documentation inherited
    public void invalidateRect (Rectangle rect)
    {
        _view.invalidateRect(rect);
    }

    /**
     * Returns the desired size for the panel based on the requested
     * and calculated bounds of the scene view.
     */
    public Dimension getPreferredSize ()
    {
        Dimension psize = (_viewmodel == null) ?
            super.getPreferredSize() : _viewmodel.bounds.getSize();
	return psize;
    }

    // documentation inherited
    public void viewChanged (int event)
    {
        // update the scene view display
        repaint();
    }

    /** The scene view data model. */
    protected IsoSceneViewModel _viewmodel;

    /** The scene view we're managing. */
    protected SceneView _view;

    /** A reference to the active sprite manager. */
    protected SpriteManager _spritemgr;

    /** A reference to the active animation manager. */
    protected AnimationManager _animmgr;
}
