/*
 * File    : Tracker.java
 * Created : 30 nov. 2003
 * By      : Olivier
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.biglybt.pif.tracker;

/**
 * @author Olivier
 *
 */

import java.net.InetAddress;
import java.util.Map;

import com.biglybt.pif.torrent.Torrent;
import com.biglybt.pif.tracker.web.TrackerWebContext;

public interface
Tracker
	extends TrackerWebContext
{
	public static final int	PR_HTTP			= 1;
	public static final int	PR_HTTPS		= 2;

		// properties for passing as, well, properties

	public static final String	PR_NON_BLOCKING		= "nonblocking";		// Boolean


	public TrackerTorrent
	host(
		Torrent		torrent,
		boolean		persistent )

		throws TrackerException;

	public TrackerTorrent
	host(
		Torrent		torrent,
		boolean		persistent,
		boolean		passive )

		throws TrackerException;

	public TrackerTorrent
	publish(
		Torrent		torrent )

		throws TrackerException;

	public TrackerTorrent
	getTorrent(
		Torrent		torrent );

    public TrackerTorrent[]
    getTorrents();

    /**
     * @Deprecated - use {@link #createWebContext(String, int, int)}
     * @param port
     * @param protocol
     * @return
     * @throws TrackerException
     */
    public TrackerWebContext
    createWebContext(
    	int		port,
		int		protocol )

    	throws TrackerException;

    	/**
    	 * Create a new web context for the given port and protocol
    	 * @param name		name of the context - will be used as basic realm for auth
    	 * @param port
    	 * @param protocol
    	 * @return
    	 * @throws TrackerException
    	 */

    public TrackerWebContext
    createWebContext(
    	String	name,
    	int		port,
		int		protocol )

    	throws TrackerException;

    	/**
    	 * Creates a new context bound to the supplied ip
    	 * @param name
    	 * @param port
    	 * @param protocol
    	 * @param bind_ip
    	 * @return
    	 * @throws TrackerException
    	 */

    public TrackerWebContext
    createWebContext(
    	String		name,
    	int			port,
		int			protocol,
		InetAddress	bind_ip )

    	throws TrackerException;

    public TrackerWebContext
    createWebContext(
    	String					name,
    	int						port,
		int						protocol,
		InetAddress				bind_ip,
		Map<String,Object>		properties )

    	throws TrackerException;

    public void
    addListener(
   		TrackerListener		listener );

    public void
    removeListener(
   		TrackerListener		listener );
}
