/*
 * Created on 26/01/2005
 * Created by Paul Duran
 * Copyright (C) 2004 Aelitis, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * AELITIS, SARL au capital de 30,000 euros
 * 8 Allee Lenotre, La Grille Royale, 78600 Le Mesnil le Roi, France.
 *
 */

package com.biglybt.ui.telnet;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Iterator;
import java.util.Set;

import com.biglybt.core.util.AEThread2;
import com.biglybt.ui.common.UIConst;
import com.biglybt.ui.console.UserProfile;
import com.biglybt.ui.console.multiuser.UserManager;

/**
 * this class is used to receive incoming connections for the telnet UI and
 * then authenticate and create a console session for the connection
 * @author pauld
 */
final class SocketServer implements Runnable
{
	private final ServerSocket serverSocket;
	private final Set allowedHosts;
	private final int maxLoginAttempts;
	private final UserManager userManager;
	private final UI ui;

	public SocketServer(UI ui, int port, Set allowedHosts, UserManager userManager, int maxLoginAttempts) throws IOException
	{
		this.ui = ui;
		this.allowedHosts = allowedHosts;
		this.userManager = userManager;
		serverSocket = new ServerSocket(port);
		this.maxLoginAttempts = maxLoginAttempts;
	}

	/**
	 * start up the server socket and when a new connection is received, check that
	 * the source address is in our permitted list and if so, start a new console input
	 * on that socket.
	 */
	@Override
	public void run()
	{
		int threadNum = 1;
		System.out.println("Telnet server started. Listening on port: " + serverSocket.getLocalPort());

		while( true ) {
			try {
				Socket socket = serverSocket.accept();

				InetSocketAddress addr = (InetSocketAddress) socket.getRemoteSocketAddress();

				if( addr.isUnresolved() || ! isAllowed(addr) ) {
					System.out.println("TelnetUI: rejecting connection from: " + addr + " as address is not allowed");
					socket.close();
				}
				else {
					System.out.println("TelnetUI: accepting connection from: " + addr);
					int loginAttempts = 0;

					while( true ) {
						// TODO: might want to put this in another thread so the port doesnt block while the user logs in

						//System.out.println("TelnetUI: starting login" );

						UserProfile profile = login( socket.getInputStream(), socket.getOutputStream() );

						//System.out.println("TelnetUI: login profile obtained" );

						if( profile != null ) {

							//System.out.println("TelnetUI: creating console input" );

							ui.createNewConsoleInput("Telnet Console " + threadNum++, socket.getInputStream(), new PrintStream(socket.getOutputStream()), profile);
							break;
						}

						//System.out.println("TelnetUI: failed to obtain login profile" );

						loginAttempts++;

						if( loginAttempts >= maxLoginAttempts ) {
							System.out.println("TelnetUI: rejecting connection from: " + addr + " as number of failed connections > max login attempts (" + maxLoginAttempts + ")");
							socket.close();
							break;
						}
					}
				}
			}
			catch (Throwable t) {
				t.printStackTrace();
				break;
			}
		}
	}
	/**
	 * if usermanager is null (ie: multi user is not enabled), returns the default user profile
	 * otherwise, requests username and password and authenticates user before returning
	 * the user profile for this user
	 * @param in input stream to read from
	 * @param out stream to write messages to
	 * @return username if login was successful, null otherwise
	 * @throws IOException
	 */
	private UserProfile login(InputStream in, OutputStream out) throws IOException
	{
		if( userManager == null )
			return UserProfile.DEFAULT_USER_PROFILE;

		PrintStream ps = new PrintStream(out);
		BufferedReader br = new BufferedReader(new InputStreamReader(in));
		ps.print("Username: ");
		String username = br.readLine();
		ps.print("Password: ");
		String password = br.readLine();
		UserProfile userProfile = userManager.authenticate(username, password);
		if( userProfile != null )
		{
			ps.println("Login successful");
			return userProfile;
		}
		ps.println("Login failed");
		return null;
	}

	/**
	 * check that the specified host/ip is allowed
	 * @param addr
	 * @return
	 */
	private boolean isAllowed(InetSocketAddress addr) {
		InetAddress address = addr.getAddress();
		if( checkHost(address.getHostAddress()) )
			return true;
		else if( checkHost(address.getHostName()))
			return true;
		else
			return false;
	}
	/**
	 * compare the specified host (might be a hostname or an IP - dont really care)
	 * and see if it is a match against one of the allowed hosts
	 * @param hostName
	 * @return true if this hostname matches one in our allowed hosts
	 */
	private boolean checkHost(String hostName) {
		if( hostName == null )
			return false;
		hostName = hostName.toLowerCase();
//			System.out.println("checking host: " + hostName);
		for (Iterator iter = allowedHosts.iterator(); iter.hasNext();) {
			String allowedHost = (String) iter.next();
			if( hostName.equals(allowedHost) )
				return true;
		}
		return false;
	}
}