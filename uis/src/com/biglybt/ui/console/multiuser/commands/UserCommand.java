/*
 * Created on 10/02/2005
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

package com.biglybt.ui.console.multiuser.commands;

import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import com.biglybt.ui.console.ConsoleInput;
import com.biglybt.ui.console.UserProfile;
import com.biglybt.ui.console.commands.CommandCollection;
import com.biglybt.ui.console.commands.IConsoleCommand;
import com.biglybt.ui.console.commands.OptionsConsoleCommand;
import com.biglybt.ui.console.multiuser.UserManager;

/**
 * container command for commands that deal with the add/modify/delete of users
 * @author pauld
 */
public class UserCommand extends IConsoleCommand {

	private final CommandCollection subCommands = new CommandCollection();
	private final UserManager userManager;

	/**
	 * @param _commandNames
	 */
	public UserCommand(UserManager userManager) {
		super("user");
		this.userManager = userManager;

		subCommands.add(new AddUserCommand());
		subCommands.add(new DeleteUserCommand());
		subCommands.add(new ModifyUserCommand());
		subCommands.add(new ListUsersCommand());
	}

	/**
	 * returns the UserManager object that is used
	 * by our subcommands
	 * @return
	 */
	private UserManager getUserManager()
	{
		return userManager;
	}

	/**
	 * write the user manager configuration back to the path that it was read from
	 * @param out stream to print success/failure messages to
	 */
	private void saveUserManagerConfig(PrintStream out)
	{
		try {
			userManager.save();
			out.println("> User Manager config saved");
		} catch (FileNotFoundException e) {
			out.println("> Error saving User Manager config: " + e);
			e.printStackTrace(out);
		}
	}

	/**
	 * returns the string describing how this command can be used
	 */
	@Override
	public String getCommandDescriptions()
	{
		return "user add|delete|list|modify <options>\tmanage users able to log in via telnet ui";
	}

	/**
	 * determine the correct subcommand and execute it
	 */
	@Override
	public void execute(String commandName, ConsoleInput ci, List args)
	{
		if( args.isEmpty() )
		{
			printHelp(ci.out, args);
		}
		else
		{
			commandName = (String) args.remove(0);
			subCommands.execute(commandName, ci, args);
		}
	}

	/**
	 * prints out the help message showing the syntax for all subcommands
	 */
	@Override
	public void printHelpExtra(PrintStream out, List args)
	{
		out.println("> -----");
		out.println("'user' syntax:");
		if( args.size() > 0 ) {
			String command = (String) args.remove(0);
			IConsoleCommand cmd = subCommands.get(command);
			if( cmd != null )
				cmd.printHelp(out, args);
			return;
		}
		out.println("user <command> <command options>");
		out.println();
		out.println("Available <command>s:");
		for (Iterator iter = subCommands.iterator(); iter.hasNext();) {
			IConsoleCommand cmd = (IConsoleCommand) iter.next();
			out.println(cmd.getCommandDescriptions());
		}
		out.println("try 'help user <command>' for more information about a particular user command");
		out.println("> -----");
	}

	/**
	 * command that adds new users to the user manager
	 * @author pauld
	 */
	private final class AddUserCommand extends OptionsConsoleCommand
	{

		public AddUserCommand()
		{
			super("add", "a");
			getOptions().addOption(new Option("u", "username", true, "name of new user"));
			getOptions().addOption(new Option("p", "password", true, "password for new user"));
			getOptions().addOption(new Option("t", "type", true, "user type (Admin / User / Guest)"));
			getOptions().addOption(new Option("d", "savedirectory", true, "default torrent save directory for this user"));
		}

		/**
		 * adds a new user
		 */
		@Override
		public void execute(String commandName, ConsoleInput ci, CommandLine commandLine)
		{
			String userName = commandLine.getOptionValue('u');
			if( userName == null )
			{
				ci.out.println("> AddUser: (u)sername option not specified");
				return;
			}

			String password = commandLine.getOptionValue('p');
			if( password == null )
			{
				ci.out.println("> AddUser: (p)assword option not specified");
				return;
			}

			String userType = commandLine.getOptionValue('t', UserProfile.DEFAULT_USER_TYPE);
			if( ! UserProfile.isValidUserType(userType.toLowerCase()))
			{
				ci.out.println("> AddUser: invalid profile type '" + userType + "'. Valid values are: " + UserProfile.ADMIN + "," + UserProfile.USER + "," + UserProfile.GUEST);
				return;
			}

			// check that a user with that name doesnt already exist
			if( getUserManager().getUser(userName) != null )
			{
				ci.out.println("> AddUser error: user '" + userName + "' already exists");
				return;
			}

			UserProfile profile = new UserProfile(userName, userType);
			profile.setPassword(password);
			String defaultSaveDirectory = commandLine.getOptionValue('d', (String) null);
			profile.setDefaultSaveDirectory(defaultSaveDirectory);

			getUserManager().addUser(profile);
			ci.out.println("> AddUser: user '" + userName + "' added");
			saveUserManagerConfig(ci.out);
		}

		@Override
		public String getCommandDescriptions() {
			return "add [-u user] <options>\t\ta\tadds a new user";
		}
	}

	/**
	 * command that deletes a user from the user manager
	 * @author pauld
	 */
	private final class DeleteUserCommand extends OptionsConsoleCommand
	{

		public DeleteUserCommand()
		{
			super("delete", "d");
			getOptions().addOption(new Option("u", "username", true, "name of user to delete"));
		}

		@Override
		public void execute(String commandName, ConsoleInput ci, CommandLine commandLine)
		{
			String userName = commandLine.getOptionValue('u');
			if( userName == null )
			{
				ci.out.println("> DeleteUser: (u)sername option not specified");
				return;
			}

			if( getUserManager().getUser(userName) == null )
			{
				ci.out.println("> DeleteUser: error - user '" + userName + "' not found");
				return;
			}

			getUserManager().deleteUser( userName );
			ci.out.println("> DeleteUser: user '" + userName + "' deleted");
			saveUserManagerConfig(ci.out);
		}

		@Override
		public String getCommandDescriptions() {
			return "delete [-u user]\t\td\tdeletes a user";
		}
	}

	/**
	 * command that changes user's password or level or default save directory
	 * @author pauld
	 */
	private final class ModifyUserCommand extends OptionsConsoleCommand
	{

		public ModifyUserCommand()
		{
			super("modify", "m");

			getOptions().addOption(new Option("u", "username", true, "name of user to modify"));
			getOptions().addOption(new Option("p", "password", true, "password for new user"));
			getOptions().addOption(new Option("t", "type", true, "user type (Admin / User / Guest)"));
			getOptions().addOption(new Option("d", "savedirectory", true, "default torrent save directory for this user"));

		}

		@Override
		public void execute(String commandName, ConsoleInput ci, CommandLine commandLine)
		{
			String userName = commandLine.getOptionValue('u');
			if( userName == null )
			{
				ci.out.println("> ModifyUser: (u)sername option not specified");
				return;
			}

			UserProfile profile = getUserManager().getUser(userName);
			if( profile == null )
			{
				ci.out.println("> ModifyUser: error - user '" + userName + "' not found");
				return;
			}

			boolean modified = false;

			String userType = commandLine.getOptionValue('t');
			if( userType != null )
			{
				if( UserProfile.isValidUserType(userType.toLowerCase()))
				{
					profile.setUserType(userType.toLowerCase());
					modified = true;
				}
				else
				{
					ci.out.println("> ModifyUser: invalid profile type '" + userType + "'. Valid values are: " + UserProfile.ADMIN + "," + UserProfile.USER + "," + UserProfile.GUEST);
					return;
				}
			}

			String password = commandLine.getOptionValue('p');
			if( password != null )
			{
				profile.setPassword(password);
				modified = true;
			}
			String defaultSaveDirectory = commandLine.getOptionValue('d');

			if( defaultSaveDirectory != null ){

				modified = true;

				if (  defaultSaveDirectory.length() > 0 ){

					profile.setDefaultSaveDirectory(defaultSaveDirectory);
				}else{

					profile.setDefaultSaveDirectory(null);
				}
			}

			if( modified )
			{
				ci.out.println("> ModifyUser: user '" + userName + "' modified");
				saveUserManagerConfig(ci.out);
			}
			else
				printHelp(ci.out, commandLine.getArgList());
		}

		@Override
		public String getCommandDescriptions() {
			return "modify [-u user] <options>\tm\tmodifies a user";
		}
	}

	/**
	 * command that prints out the list of users registered in this user manager
	 * @author pauld
	 */
	private final class ListUsersCommand extends IConsoleCommand
	{
		public ListUsersCommand()
		{
			super("list", "l");
		}

		@Override
		public void execute(String commandName, ConsoleInput ci, List args)
		{
			ci.out.println("> -----");
			ci.out.println("> Username\tProfile\t\tSave Directory");
			for (Iterator iter = getUserManager().getUsers().iterator(); iter.hasNext();) {
				UserProfile profile = (UserProfile) iter.next();
				String saveDir = profile.getDefaultSaveDirectory();
				if( saveDir == null ) saveDir = "(default)";
				ci.out.println("> " + profile.getUsername() + "\t\t" + profile.getUserType() + "\t\t" + saveDir);
			}
			ci.out.println("> -----");
		}

		@Override
		public String getCommandDescriptions() {
			return "list \t\t\t\tl	lists all users";
		}
	}

}
