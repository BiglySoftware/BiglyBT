/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 */

package com.biglybt.ui.console.commands;

import java.io.PrintStream;
import java.util.*;

import com.biglybt.core.util.Debug;
import com.biglybt.ui.console.ConsoleInput;

import com.biglybt.core.pairing.*;


public class Pairing extends IConsoleCommand {

	public Pairing()
	{
		super("pairing", "pair");
	}

	@Override
	public String getCommandDescriptions()
	{
		return("pairing\t\tpair\tShows and modified the current Vuze remote pairing state.");
	}

	@Override
	public void printHelpExtra(PrintStream out, List args) {
		out.println("> -----");
		out.println("Subcommands:");
		out.println("enable\tEnable remote pairing");
		out.println("disable\tDisable remote pairing");
		out.println("> -----");
	}

	@Override
	public void
	execute(
		String commandName, ConsoleInput ci, List<String> args) {

		PairingManager pm = PairingManagerFactory.getSingleton();

		if ( args.size() > 0 ){

			String sub = args.get(0);

			if ( sub.equals( "enable" )){

				pm.setEnabled( true );

			}else if ( sub.equals( "disable" )){

				pm.setEnabled( false );

			}else{

				ci.out.println( "Unsupported sub-command: " + sub );

				return;
			}
		}

		ci.out.println( "Current pairing state:" );

		if ( pm.isEnabled()){

			ci.out.println( "\tStatus:      " + pm.getStatus());

			try{
				ci.out.println( "\tAccess code: " + pm.getAccessCode());

			}catch( Throwable e ){

				ci.out.println( "Failed to get access code: " + Debug.getNestedExceptionMessage( e ));
			}
		}else{
			ci.out.println( "\tdisabled" );
		}

	}
}
