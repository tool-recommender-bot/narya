//
// $Id: UniformTileSetRuleSet.java,v 1.5 2001/11/29 20:39:37 mdb Exp $

package com.threerings.media.tools.tile.xml;

import org.xml.sax.Attributes;
import org.apache.commons.digester.Digester;

import com.threerings.media.tile.TileSet;
import com.threerings.media.tile.UniformTileSet;

/**
 * Parses {@link UniformTileSet} instances from a tileset description. A
 * uniform tileset description looks like so:
 *
 * <pre>
 * &lt;tileset name="Sample Uniform Tileset"&gt;
 *   &lt;imagePath&gt;path/to/image.png&lt;/imagePath&gt;
 *   &lt;!-- the width of each tile in pixels --&gt;
 *   &lt;width&gt;64&lt;/width&gt;
 *   &lt;!-- the height of each tile in pixels --&gt;
 *   &lt;height&gt;48&lt;/height&gt;
 *   &lt;!-- the total number of tiles in the set --&gt;
 *   &lt;tileCount&gt;16&lt;/tileCount&gt;
 * &lt;/tileset&gt;
 * </pre>
 */
public class UniformTileSetRuleSet extends TileSetRuleSet
{
    // documentation inherited
    public void addRuleInstances (Digester digester)
    {
        super.addRuleInstances(digester);

        digester.addCallMethod(
            _prefix + TILESET_PATH + "/width", "setWidth", 0,
            new Class[] { java.lang.Integer.TYPE });
        digester.addCallMethod(
            _prefix + TILESET_PATH + "/height", "setHeight", 0,
            new Class[] { java.lang.Integer.TYPE });
        digester.addCallMethod(
            _prefix + TILESET_PATH + "/tileCount", "setTileCount", 0,
            new Class[] { java.lang.Integer.TYPE });
    }

    // documentation inherited
    protected Class getTileSetClass ()
    {
        return UniformTileSet.class;
    }
}
