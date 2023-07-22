/*
 * Created on 23-Nov-2004
 * Created by Paul Gardner
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
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 * AELITIS, SARL au capital de 30,000 euros
 * 8 Allee Lenotre, La Grille Royale, 78600 Le Mesnil le Roi, France.
 *
 */

package com.biglybt.ui.telnet;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;

import com.biglybt.core.Core;
import com.biglybt.core.CoreLifecycleAdapter;
import com.biglybt.core.util.SystemProperties;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.torrentdownloader.TorrentDownloaderFactory;
import com.biglybt.ui.common.IUserInterface;
import com.biglybt.ui.console.ConsoleInput;
import com.biglybt.ui.console.UserProfile;
import com.biglybt.ui.console.multiuser.MultiUserConsoleInput;
import com.biglybt.ui.console.multiuser.UserManager;
import com.biglybt.ui.console.multiuser.commands.UserCommand;

/**
 * this is a telnet UI that starts up a server socket that listens for new connections
 * on a (configurable) port. when an incoming connection is received, we check the host
 * against our list of allowed hosts and if this host is permitted, we start a new
 * command line interface for that connection.
 * @author fatal
 */
public class UI extends com.biglybt.ui.common.UITemplateHeadless implements IUserInterface
{
	@Override
	public void buildCommandLine(Options options) {

	}

	@Override
	public String[] processArgs(CommandLine commands, String[] args) {
		int telnetPort = COConfigurationManager.getIntParameter("Telnet_iPort", 57006);
		System.out.println("Telnet port: " + telnetPort);

		return args;
	}

	private UserManager userManager;

	@Override
	public void coreCreated(Core core) {
		super.coreCreated(core);
		if (core.isStarted()) {
			startUI();
			return;
		}
		core.addLifecycleListener(new CoreLifecycleAdapter() {
			@Override
			public void started(Core core) {
				startUI();
			}
		});
	}

	/**
	 * start up a server socket thread on an appropriate port as obtained from the configuration manager.
	 */
	private void startUI() {
		try {
			int telnetPort = COConfigurationManager.getIntParameter("Telnet_iPort", 57006);
			String allowedHostsStr = COConfigurationManager.getStringParameter("Telnet_sAllowedHosts", "127.0.0.1,titan");
			StringTokenizer st = new StringTokenizer(allowedHostsStr, ",");
			Set allowedHosts = new HashSet();
			while( st.hasMoreTokens() )
				allowedHosts.add(st.nextToken().toLowerCase());
			int maxLoginAttempts = COConfigurationManager.getIntParameter("Telnet_iMaxLoginAttempts", 3);
			userManager = initUserManager();
			Thread thread = new Thread(new SocketServer(this, telnetPort, allowedHosts, userManager, maxLoginAttempts), "Telnet Socket Server Thread");
			thread.setDaemon(true);
			thread.start();
		} catch (IOException e) {
			e.printStackTrace();
		}

	    TorrentDownloaderFactory.initManager(core.getGlobalManager(), true );
	}

	/**
	 * @return user manager instance if multi user is enabled, otherwise null
	 */
	private UserManager initUserManager()
	{
		if( System.getProperty(SystemProperties.SYSPROP_CONSOLE_MULTIUSER) != null )
			return UserManager.getInstance(core.getPluginManager().getDefaultPluginInterface());
		else
			return null;
	}

	/**
	 * creates a new console input using the specified input/output streams.
	 * we create the new input in non-controlling mode because we don't want the 'quit'
	 * command to shut down the whole interface - simply this clients connection.
	 * @param consoleName
	 * @param inputStream
	 * @param outputStream
	 * @param profile
	 */
	public void createNewConsoleInput(String consoleName, InputStream inputStream, PrintStream outputStream, UserProfile profile)
	{
		ConsoleInput console;
		if( userManager != null )
		{
			MultiUserConsoleInput muc = new MultiUserConsoleInput(consoleName, core, new InputStreamReader(inputStream), outputStream, Boolean.FALSE, profile);
			muc.registerCommand( new UserCommand(userManager) );
			console = muc;
		}
		else
		{
			console = new ConsoleInput(consoleName, core, new InputStreamReader(inputStream), outputStream, Boolean.FALSE, profile);

			System.out.println( "TelnetUI: console input instantiated" );
		}
		console.printwelcome();
		console.printconsolehelp();
	}
}