/*
 * Created on 30/08/2005
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

package com.biglybt.ui.console.commands;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.biglybt.ui.console.ConsoleInput;

/**
 * this class represents a collection of commands. it can be used by
 * command objects to house their subcommands. when execute() method
 * is called, the appropriate subcommand is looked up and executed
 * @author pauld
 */
public class CommandCollection
{
	private final Map subCommands = new HashMap();

	/**
	 * determines the appropriate subcommand to execute and then
	 * executes it, passing in the arguments that we received
	 * @param commandName
	 * @param ci
	 * @param args
	 */
	public void execute(String commandName, ConsoleInput ci, List args)
	{
		IConsoleCommand command = get(commandName);
		command.execute(commandName, ci, args);
	}

	/**
	 * constructs a string with the descriptions of all of the subcommands,
	 * each separated by a newline
	 * @return
	 */
	public String getCommandDescriptions()
	{
		StringWriter sw = new StringWriter();
		PrintWriter out = new PrintWriter(sw);
		for (Iterator iter = iterator(); iter.hasNext();) {
			IConsoleCommand cmd = (IConsoleCommand) iter.next();
			out.println(cmd.getCommandDescriptions());
		}
		return sw.toString();
	}

	/**
	 * returns the sub command with the specified command name
	 * @param commandName
	 * @return
	 */
	public IConsoleCommand get(String commandName)
	{
		return (IConsoleCommand) subCommands.get(commandName);
	}

	/**
	 * adds the specified console command as a subcommand to this object.
	 * we will therefore respond to all of the subcommands command names
	 * when passed as the first argument to this command
	 * @param command
	 */
	public void add(IConsoleCommand command)
	{
		for (Iterator iter = command.getCommandNames().iterator(); iter.hasNext();) {
			String cmdName = (String) iter.next();
			subCommands.put(cmdName, command);
		}
	}

	/**
	 * gets the set of IConsoleCommand objects that are all
	 * of the subcommands that this object owns
	 * @return
	 */
	public Iterator iterator()
	{
		return new HashSet(subCommands.values()).iterator();
	}
}
