//
// $Id$
//
// Narya library - tools for developing networked games
// Copyright (C) 2002-2012 Three Rings Design, Inc., All Rights Reserved
// http://code.google.com/p/narya/
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

package com.threerings.crowd.chat.data {

import com.threerings.util.Byte;

import com.threerings.presents.data.InvocationMarshaller;

import com.threerings.crowd.chat.client.ChannelSpeakService;

/**
 * Provides the implementation of the <code>ChannelSpeakService</code> interface
 * that marshalls the arguments and delivers the request to the provider
 * on the server. Also provides an implementation of the response listener
 * interfaces that marshall the response arguments and deliver them back
 * to the requesting client.
 */
public class ChannelSpeakMarshaller extends InvocationMarshaller
    implements ChannelSpeakService
{
    /** The method id used to dispatch <code>speak</code> requests. */
    public static const SPEAK :int = 1;

    // from interface ChannelSpeakService
    public function speak (arg1 :ChatChannel, arg2 :String, arg3 :int) :void
    {
        sendRequest(SPEAK, [
            arg1, arg2, Byte.valueOf(arg3)
        ]);
    }
}
}
