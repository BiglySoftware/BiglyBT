/*
 * Written and copyright 2001-2004 Tobias Minich. Distributed under the GNU
 * General Public License; see the README file. This code comes with NO
 * WARRANTY.
 *
 * Move.java
 *
 * Created on 23.03.2004
 *
 */
package com.biglybt.ui.console.commands;

import java.util.*;

import com.biglybt.core.util.AERunStateHandler;
import com.biglybt.ui.console.ConsoleInput;

/**
 * @author Tobias Minich
 */
public class RunState extends IConsoleCommand {

	public RunState()
	{
		super("runstate", "rs");
	}

	@Override
	public String getCommandDescriptions()
	{
		return("runstate\t\trs\tShows and modified the current Vuze run-state.");
	}

	@Override
	public void execute(String commandName, ConsoleInput ci, List<String> args) {

		ci.out.println( "Current run state:" );

		long mode = AERunStateHandler.getResourceMode();

		Map<String,Long>	mode_map = new HashMap<>();

		mode_map.put( "all", AERunStateHandler.RS_ALL_LOW );

		for ( int i=0;i<AERunStateHandler.RS_MODES.length;i++){

			String 	mode_name 	= AERunStateHandler.RS_MODE_NAMES[i];
			long 	mode_value 	= AERunStateHandler.RS_MODES[i];

			String[] bits = mode_name.split(":");

			for ( String bit: bits ){

				mode_map.put( bit.trim().toLowerCase(), mode_value );
			}
		}

		boolean	bad = false;

		for ( String arg: args ){

			String[] bits = arg.split( "=" );

			if ( bits.length != 2 ){

				bad = true;

				break;
			}

			Long this_mode = mode_map.get( bits[0].toLowerCase());

			if ( this_mode == null ){

				bad = true;

			}else{

				boolean	on = false;

				String rhs = bits[1].toLowerCase();

				if ( rhs.equals( "on" )){

					on = true;

				}else if ( rhs.equals( "off" )){

				}else{

					bad = true;
				}

				if ( !bad ){

					if ( on ){

						mode |= this_mode;
					}else{
						mode &= ~this_mode;
					}

					AERunStateHandler.setResourceMode( mode );
				}
			}
		}

		if ( bad ){

			ci.out.println("> Command 'runstate': invalid parameters (example: dui=On, all=off)" );

		}else{

			for ( int i=0;i<AERunStateHandler.RS_MODES.length;i++){

				String 	mode_name 	= AERunStateHandler.RS_MODE_NAMES[i];
				long 	mode_value 	= AERunStateHandler.RS_MODES[i];

				ci.out.println( "\t" + mode_name + "=" + ((mode&mode_value)==0?"Off":"On" ));
			}
		}
	}
}
