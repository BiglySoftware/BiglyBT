/*
 * Written and copyright 2001-2004 Tobias Minich. Distributed under the GNU
 * General Public License; see the README file. This code comes with NO
 * WARRANTY.
 *
 * XML.java
 *
 * Created on 22.03.2004
 *
 */
package com.biglybt.ui.console.commands;

import java.io.FileOutputStream;
import java.util.List;
import com.biglybt.core.stats.StatsWriterFactory;
import com.biglybt.core.stats.StatsWriterStreamer;
import com.biglybt.ui.console.ConsoleInput;

/**
 * @author tobi
 */
public class XML extends IConsoleCommand {

	public XML()
	{
		super("xml");
	}

	@Override
	public String getCommandDescriptions() {
		return("xml [<file>]\t\t\t\tOutput stats in xml format (to <file> if given)");
	}

	@Override
	public void execute(String commandName, ConsoleInput ci, List args) {
		StatsWriterStreamer sws = StatsWriterFactory.createStreamer(ci.getCore());
		String file = null;
		if ((args != null) && (!args.isEmpty()))
				file = (String) args.get(0);
		if (file == null) {
			try {
				ci.out.println("> -----");
				sws.write(ci.out);
				ci.out.println("> -----");
			} catch (Exception e) {
				ci.out.println("> Exception while trying to output xml stats:" + e.getMessage());
			}
		} else {
			try {
				FileOutputStream os = new FileOutputStream(file);

				try {

					sws.write(os);

				} finally {

					os.close();
				}
				ci.out.println("> XML stats successfully written to " + file);
			} catch (Exception e) {
				ci.out.println("> Exception while trying to write xml stats:" + e.getMessage());
			}
		}
	}
}
