/*
 * Copyright (C) Bigly Software.  All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package com.biglybt.core.util.protocol;

import java.net.URL;

/**
 * When this interface is present on one of our {@link java.net.URLStreamHandler}
 * classes, the handler may be able to process the url without opening a
 * connection or creating an InputStream.
 * <p/>
 * The primary example for this is proto://install-plugin/pluginname
 * which fires off the plugin installer
 */
public interface AzURLStreamHandlerSkipConnection {
	/**
	 * Determines if an URL can be processed without {@link URL#openConnection()}
	 * or {@link URL#openStream()}
	 *
	 * @param checkUrl URL to check
	 * @param processUrlNow If URL can be processed without a connection, true will process the URL
	 * @return
	 */
	boolean canProcessWithoutConnection(URL checkUrl, boolean processUrlNow);
}
