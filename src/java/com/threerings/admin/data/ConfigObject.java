//
// $Id: ConfigObject.java,v 1.1 2004/03/04 02:42:31 eric Exp $

package com.threerings.admin.client;

import javax.swing.JPanel;

import com.threerings.presents.dobj.DObject;
import com.threerings.presents.util.PresentsContext;

/**
 * Base class for runtime config distributed objects.  Used to allow
 * config objects to supply custom object editing UI.
 */
public class ConfigObject extends DObject
{
    /**
     * Returns a custom editor panel for the specified field.
     */
    public JPanel getCustomEditor (PresentsContext ctx, String fieldName)
    {
        return null;
    }
}
