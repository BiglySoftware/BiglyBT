/*
 * Created on 3/02/2005
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

package com.biglybt.ui.console.multiuser;

import java.io.PrintStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.biglybt.core.Core;
import com.biglybt.ui.console.ConsoleInput;
import com.biglybt.ui.console.UserProfile;
import com.biglybt.ui.console.commands.IConsoleCommand;
import com.biglybt.ui.console.multiuser.commands.Show;

/**
 * subclass of the ConsoleInput object that is used for multi users.
 * in this particular subclass, we replace some commands with our own versions
 * and disable some commands.
 * @author pauld
 */
public class MultiUserConsoleInput extends ConsoleInput
{
	// commands that are executable with admin role
	private List adminCommands;

	// commands that are executable with user role
	private List userCommands;

	/**
	 * set up the lists of commands that we prohibit, based upon the user type.
	 * @param con
	 * @param core
	 * @param _in
	 * @param _out
	 * @param _controlling
	 * @param profile
	 */
	public MultiUserConsoleInput(String con, Core core,
			Reader _in, PrintStream _out, Boolean _controlling,
			UserProfile profile) {
		super(con, core, _in, _out, _controlling, profile);
	}

	/**
	 * initialize our list of commands that need specific roles
	 */
	@Override
	protected void initialise() {

		adminCommands = new ArrayList();
		adminCommands.add("quit");
		adminCommands.add("share");
		adminCommands.add("user");
		// move command is admin only so that standard users cannot
		// prioritise their torrents
		adminCommands.add("move");
		adminCommands.add("log");
		adminCommands.add("ui");

		userCommands = new ArrayList();
		userCommands.add("set");
		userCommands.add("alias");
		userCommands.add("add");

		super.initialise();
	}

	/**
	 * add some multi-user specific commands
	 */
	@Override
	protected void registerCommands() {
		super.registerCommands();
		// this will override the original Show command
		registerCommand(new Show());
	}

	/**
	 * check whether the specified command is one of our banned commands for
	 * this particular user type. some commands are able to handle different
	 * user types, others are not relevant to anybody but admin
	 */
	@Override
	public void registerCommand(IConsoleCommand command) {
		if( ! UserProfile.ADMIN.equals( getUserProfile().getUserType() ) )
		{
			Set commandNames = command.getCommandNames();
			for (Iterator iter = commandNames.iterator(); iter.hasNext();) {
				String cmdName = (String) iter.next();
				if( adminCommands.contains(cmdName) )
					return;
				if( ! UserProfile.USER.equals( getUserProfile().getUserType() ) )
				{
					if( userCommands.contains(cmdName))
						return;
				}
			}
		}
		super.registerCommand(command);
	}
}
