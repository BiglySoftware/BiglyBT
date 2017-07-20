/*
 * Created on May 19, 2008
 * Created by Paul Gardner
 *
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
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
 */


package com.biglybt.util;

import java.io.File;
import java.util.*;

import com.biglybt.core.util.CopyOnWriteList;
import com.biglybt.core.util.Debug;
import com.biglybt.core.vuzefile.VuzeFile;
import com.biglybt.core.vuzefile.VuzeFileComponent;
import com.biglybt.core.vuzefile.VuzeFileHandler;
import com.biglybt.core.vuzefile.VuzeFileProcessor;

public class
NavigationHelper
{
	public static final int COMMAND_SWITCH_TO_TAB	= 1;
	public static final int COMMAND_CONDITION_CHECK	= 2;


	private static CopyOnWriteList	listeners = new CopyOnWriteList();
	private static List				command_queue;

	protected static void
	initialise()
	{
		VuzeFileHandler.getSingleton().addProcessor(
			new VuzeFileProcessor()
			{
				@Override
				public void
				process(
					VuzeFile[]		files,
					int				expected_types )
				{
					for (int i=0;i<files.length;i++){

						VuzeFile	vf = files[i];

						VuzeFileComponent[] comps = vf.getComponents();

						for (int j=0;j<comps.length;j++){

							VuzeFileComponent comp = comps[j];

							if ( 	comp.getType() == VuzeFileComponent.COMP_TYPE_V3_NAVIGATION ||
									comp.getType() == VuzeFileComponent.COMP_TYPE_V3_CONDITION_CHECK ){

								try{

									List commands = (List)comp.getContent().get("commands");

									for ( int k=0;k<commands.size();k++){

										Map	command = (Map)commands.get(k);

										int	command_type = ((Long)command.get("type")).intValue();

										List	l_args = (List)command.get( "args" );

										String[]	args;

										if ( l_args == null ){

											args = new String[0];

										}else{

											args = new String[l_args.size()];

											for (int l=0;l<args.length;l++){

												args[l] = new String((byte[])l_args.get(l), "UTF-8" );
											}
										}

										addCommand( command_type, args );
									}

									comp.setProcessed();

								}catch( Throwable e ){

									Debug.printStackTrace(e);
								}
							}
						}
					}
				}
			});
	}

	protected static void
	addCommand(
		int			type,
		String[]	args )
	{
			// guarantee delivery to at least one listener by queueing if none

		synchronized( listeners ){

			if ( listeners.size() == 0 ){

				if ( command_queue == null ){

					command_queue = new ArrayList();
				}

				command_queue.add( new Object[]{ new Integer( type ), args });
			}
		}

			// possible duplicate delivery - assumed not a problem

		Iterator	it = listeners.iterator();

		while( it.hasNext()){

			navigationListener l = (navigationListener)it.next();

			try{
				l.processCommand( type, args );

			}catch( Throwable e ){

				Debug.printStackTrace(e);
			}
		}
	}

	public static void
	addListener(
		navigationListener		l )
	{
		List	queue;

		synchronized( listeners ){

			listeners.add( l );

			queue = command_queue;

			command_queue = null;
		}

		if ( queue != null ){

			for (int i=0;i<queue.size();i++){

				Object[]	entry = (Object[])queue.get(i);

				int			type = ((Integer)entry[0]).intValue();
				String[]	args = (String[])entry[1];

				try{
					l.processCommand( type, args );

				}catch( Throwable e ){

					Debug.printStackTrace( e );
				}
			}
		}
	}

	public static void
	removeListener(
				navigationListener l )
	{
		synchronized( listeners ){

			listeners.remove( l );
		}

	}

	public interface
	navigationListener
	{
		public void
		processCommand(
			int			type,
			String[]	args );
	}

	public static void
	main(
		String[]	args )
	{
		try{
			VuzeFile vf = VuzeFileHandler.getSingleton().create();

			Map	content = new HashMap();

			List	commands = new ArrayList();

			content.put( "commands", commands );

				// home tab

			Map	command1 = new HashMap();

			commands.add( command1 );

			List	l_args1 = new ArrayList();

			//l_args1.add( SkinConstants.VIEWID_HOME_TAB  );

			command1.put( "type", new Long( COMMAND_SWITCH_TO_TAB ));
			command1.put( "args", l_args1 );

				// activity tab

			Map	command2 = new HashMap();

			commands.add( command2 );

			List	l_args2 = new ArrayList();

			//l_args2.add( SkinConstants.VIEWID_ACTIVITY_TAB );

			command2.put( "type", new Long( COMMAND_SWITCH_TO_TAB ));
			command2.put( "args", l_args2 );

				// check plugin available

			Map	command3 = new HashMap();

			commands.add( command3 );

			List	l_args3 = new ArrayList();

			command3.put( "type", new Long( COMMAND_CONDITION_CHECK ));
			command3.put( "args", l_args3 );

			vf.addComponent( VuzeFileComponent.COMP_TYPE_V3_NAVIGATION, content );

			vf.write( new File( "C:\\temp\\v3ui.vuze" ));

		}catch( Throwable e ){

			e.printStackTrace();
		}
	}
}
