/*
 * Created on 14/12/2004
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

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import com.biglybt.ui.console.ConsoleInput;

/**
 * subclass of IConsoleCommand that allows the parameters to be defined using
 * an Options object
 * @see org.apache.commons.cli.Options
 * @author pauld
 */
public abstract class OptionsConsoleCommand extends IConsoleCommand
{
	private Options options = new Options();
	private CommandLineParser parser = null;

	public OptionsConsoleCommand(String main_name) {
		super(main_name);
	}

	public OptionsConsoleCommand(String main_name, String short_name) {
		super(main_name, short_name);
	}

	/**
	 * take the args and try and create a command line object
	 */
	@Override
	public void execute(String commandName, ConsoleInput console, List arguments) {
		CommandLineParser parser = getParser();

		try
		{
			String []args = new String[arguments.size()];
			int i = 0;
			for (Iterator iter = arguments.iterator(); iter.hasNext();) {
				String arg = (String) iter.next();
				args[i++] = arg;
			}
			CommandLine line = parser.parse(getOptions(), args);
			execute( commandName, console, line );
		} catch (ParseException e)
		{
			console.out.println(">> Invalid arguments: " + e.getMessage());
//			printHelp(commandName, console.out);
			printHelp(console.out, arguments);
		}
	}

	/* (non-Javadoc)
	 * @see com.biglybt.ui.console.commands.IConsoleCommand#printHelp(java.io.PrintStream, java.util.List)
	 */
	@Override
	public void printHelpExtra(PrintStream out, List args)
	{
		HelpFormatter formatter = new HelpFormatter();
		PrintWriter writer = new PrintWriter(out);
		writer.println("> -----");
		writer.println(getCommandDescriptions());
//		formatter.printHelp(writer, 80, getCommandDescriptions(), ">>>", getOptions(), 4, 4, ">>>", true);
		formatter.printOptions(writer, 80, getOptions(), 4, 4);
		writer.println("> -----");
		writer.flush();
	}

	/**
	 * execute using the specified command line.
	 * @param commandName
	 * @param console
	 * @param commandLine
	 */
	public abstract void execute(String commandName, ConsoleInput console, CommandLine commandLine);

	/**
	 * @return
	 */
	protected CommandLineParser getParser() {
		if( parser == null )
			parser = new PosixParser();
		return parser;
	}


	protected Options getOptions()
	{
		return options;
	}
}
