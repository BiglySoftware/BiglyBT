/*
 * Written and copyright 2001-2004 Tobias Minich. Distributed under the GNU
 * General Public License; see the README file. This code comes with NO
 * WARRANTY.
 *
 * Log.java
 *
 * Created on 23.03.2004
 *
 */
package com.biglybt.ui.console.commands;

import java.io.IOException;
import java.util.*;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.log4j.Appender;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.varia.DenyAllFilter;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.logging.LoggerChannel;
import com.biglybt.pif.logging.LoggerChannelListener;
import com.biglybt.ui.console.ConsoleInput;

/**
 * @author Tobias Minich
 */
public class Log extends OptionsConsoleCommand {

	private Map channel_listener_map = new HashMap();

	public Log()
	{
		super("log", "l");
	}

	@Override
	protected Options getOptions()
	{
		Options options = new Options();
		options.addOption( new Option("f", "filename", true, "filename to write log to"));
		return options;
	}

	@Override
	public void execute(String commandName, final ConsoleInput ci, CommandLine commandLine)
	{
		Appender con = Logger.getRootLogger().getAppender("ConsoleAppender");
		List args = commandLine.getArgList();
		if ((con != null) && (!args.isEmpty())) {
			String subcommand = (String) args.get(0);
			if ("off".equalsIgnoreCase(subcommand) ) {
				if ( args.size() == 1 ){
					con.addFilter(new DenyAllFilter());
					ci.out.println("> Console logging off");
				}else{

					String	name = (String)args.get(1);

					Object[]	entry  = (Object[])channel_listener_map.remove( name );

					if ( entry == null ){

						ci.out.println( "> Channel '" + name + "' not being logged" );

					}else{

						((LoggerChannel)entry[0]).removeListener((LoggerChannelListener)entry[1]);

						ci.out.println( "> Channel '" + name + "' logging off" );
					}
				}
			} else if ("on".equalsIgnoreCase(subcommand) ) {

				if ( args.size() == 1 ){

					if( commandLine.hasOption('f') )
					{
						// send log output to a file
						String filename = commandLine.getOptionValue('f');

						try
						{
							Appender newAppender = new FileAppender(new PatternLayout("%d{ISO8601} %c{1}-%p: %m%n"), filename, true);
							newAppender.setName("ConsoleAppender");
							Logger.getRootLogger().removeAppender(con);
							Logger.getRootLogger().addAppender(newAppender);
							ci.out.println("> Logging to filename: " + filename);
						} catch (IOException e)
						{
							ci.out.println("> Unable to log to file: " + filename + ": " + e);
						}
					}
					else
					{
						if( ! (con instanceof ConsoleAppender) )
						{
							Logger.getRootLogger().removeAppender(con);
							con = new ConsoleAppender(new PatternLayout(PatternLayout.TTCC_CONVERSION_PATTERN));
							con.setName("ConsoleAppender");
						    Logger.getRootLogger().addAppender(con);
						}
						// switch back to console appender
						ci.out.println("> Console logging on");
					}

					con.clearFilters();
				}else{
					// hack - dunno how to do plugin-specific logging using these damn appenders..

					Map	channel_map = getChannelMap( ci );

					final String	name = (String)args.get(1);

					LoggerChannel	channel = (LoggerChannel)channel_map.get(name);

					if ( channel == null ){

						ci.out.println( "> Channel '" + name + "' not found" );

					}else if ( channel_listener_map.get(name) != null ){

						ci.out.println( "> Channel '" + name + "' already being logged" );

					}else{

						LoggerChannelListener	l =
							new LoggerChannelListener()
							{
								@Override
								public void
								messageLogged(
									int		type,
									String	content )
								{
									ci.out.println( "["+name+"] "+ content );
								}

								@Override
								public void
								messageLogged(
									String		str,
									Throwable	error )
								{
									ci.out.println( "["+name+"] "+ str );

									error.printStackTrace( ci.out );
								}
							};

						channel.addListener( l );

						channel_listener_map.put( name, new Object[]{ channel, l });

						ci.out.println( "> Channel '" + name + "' on" );
					}

				}
			}else if ( subcommand.equalsIgnoreCase("list" )){

				Map	channel_map = getChannelMap( ci );

				Iterator it = channel_map.keySet().iterator();

				while( it.hasNext()){

					String	name = (String)it.next();

					ci.out.println( "  " + name + " [" + ( channel_listener_map.get( name ) == null?"off":"on") + "]" );
				}
			} else {

				ci.out.println("> Command 'log': Subcommand '" + subcommand + "' unknown.");
			}
		} else {
			ci.out.println("> Console logger not found or missing subcommand for 'log'\r\n> log syntax: log [-f filename] (on [name]|off [name]|list)");
		}
	}

	protected Map
	getChannelMap(
		ConsoleInput	ci )
	{
		Map channel_map = new HashMap();

		PluginInterface[]	pis = ci.core.getPluginManager().getPluginInterfaces();

		for (int i=0;i<pis.length;i++){

			LoggerChannel[]	logs = pis[i].getLogger().getChannels();

			if ( logs.length > 0 ){

				if ( logs.length == 1 ){

					channel_map.put( pis[i].getPluginName(),logs[0] );

				}else{

					for (int j=0;j<logs.length;j++){

						channel_map.put( pis[i].getPluginName() + "." + logs[j].getName(), logs[j] );
					}
				}
			}
		}

		return( channel_map );
	}

	public static void commandLogtest(ConsoleInput ci, List args) {
		Logger.getLogger("biglybt").fatal("Logging test" + (((args == null) || (args.isEmpty())) ? "" : ": " + args.get(0).toString()));
	}

	@Override
	public String getCommandDescriptions()
	{
		return("log [-f filename] (on [name]|off [name]|list)\t\t\tl\tTurn on/off console logging");
	}
}
