/*
 * Created on 29/12/2004
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

import java.util.Iterator;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import com.biglybt.ui.console.ConsoleInput;

/**
 * the alias command will add/remove/modify aliases to the CLI input reader.
 * aliases will be expanded and take priority over standard commands.
 * @author pauld
 */
public class Alias extends OptionsConsoleCommand {

	/**
	 * @param _commandNames
	 */
	public Alias() {
		super("alias");
		getOptions().addOption(new Option("d", "delete", false, "delete the specified alias"));
	}

	/* (non-Javadoc)
	 * @see com.biglybt.ui.console.commands.IConsoleCommand#getCommandDescriptions()
	 */
	@Override
	public String getCommandDescriptions() {
		return "alias [-d] [aliasname] [arguments...]\tadd/modify/delete aliases. use with no argument to show existing aliases";
	}

	/* (non-Javadoc)
	 * @see com.biglybt.ui.console.commands.OptionsConsoleCommand#execute(java.lang.String, com.biglybt.ui.console.ConsoleInput, org.apache.commons.cli.CommandLine)
	 */
	@Override
	public void execute(String commandName, ConsoleInput console, CommandLine commandLine) {

		List args = commandLine.getArgList();
		if( args.isEmpty() )
		{
			if( commandLine.hasOption('d') )
				console.out.println(commandName + " --delete requires the name of an alias to remove");
			else
				printAliases(console);
			return;
		}
		if( commandLine.hasOption('d') )
			deleteAlias(console, (String) args.get(0));
		else
		{
			String aliasName = (String) args.remove(0);
			if( args.isEmpty() )
			{
				printAlias(console, aliasName);
			}
			else
				addAlias( console, aliasName, args );
		}
	}

	/**
	 * @param aliasName
	 */
	private void printAlias(ConsoleInput ci, String aliasName) {
		String aliasText = (String) ci.aliases.get(aliasName);
		if( aliasText == null )
		{
			ci.out.println("> Error: Alias '" + aliasName + "' does not exist");
		}
		else
		{
			ci.out.println("> " + aliasName + "=" + aliasText);
		}
	}

	/**
	 * @param object
	 */
	private void deleteAlias(ConsoleInput ci, String aliasName) {
//		ci.out.println("removing alias: " + aliasName);
		if( ci.aliases.remove(aliasName) == null )
		{
			ci.out.println("> Error: Alias '" + aliasName + "' does not exist");
		}
		else
		{
			ci.out.println("> Alias: '" + aliasName + "' deleted");
			ci.saveAliases();
		}
	}

	/**
	 * @param object
	 * @param argList
	 */
	private void addAlias(ConsoleInput ci, String aliasName, List argList) {
//		ci.out.println("adding alias: " + aliasName);
		StringBuilder aliasText = new StringBuilder();
		for (Iterator iter = argList.iterator(); iter.hasNext();) {
			String arg = (String) iter.next();
			if(arg.contains(" "))
				aliasText.append("\"").append(arg).append("\"");
			else
				aliasText.append(arg);
			aliasText.append(" ");
		}
		ci.aliases.put(aliasName, aliasText.toString());
		ci.saveAliases();
		printAlias(ci, aliasName);
	}

	/**
	 * prints out a list of all the aliases
	 * @param out
	 */
	private void printAliases(ConsoleInput ci) {
		for (Iterator iter = ci.aliases.keySet().iterator(); iter.hasNext();) {
			String aliasName = (String)iter.next();
			printAlias(ci, aliasName);
		}
	}

}
