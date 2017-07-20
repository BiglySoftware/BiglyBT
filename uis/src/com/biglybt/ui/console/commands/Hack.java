/*
 * Written and copyright 2001-2004 Tobias Minich. Distributed under the GNU
 * General Public License; see the README file. This code comes with NO
 * WARRANTY.
 *
 * Hack.java
 *
 * Created on 22.03.2004
 *
 */
package com.biglybt.ui.console.commands;
import java.io.PrintStream;
import java.net.URI;
import java.net.URL;
import java.util.Iterator;
import java.util.List;

import com.biglybt.core.category.Category;
import com.biglybt.core.category.CategoryManager;
import com.biglybt.core.disk.DiskManager;
import com.biglybt.core.disk.DiskManagerFileInfo;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.tracker.client.TRTrackerAnnouncer;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.ui.console.ConsoleInput;

import com.biglybt.core.tag.Tag;
import com.biglybt.core.tag.TagManager;
import com.biglybt.core.tag.TagManagerFactory;
import com.biglybt.core.tag.TagType;

/**
 * @author Tobias Minich
 */
public class Hack extends TorrentCommand
{

	private final CommandCollection subCommands = new CommandCollection();

	public Hack()
	{
		super("hack", "#", "Hacking");
		subCommands.add(new HackFile());
		subCommands.add(new HackTracker());
		subCommands.add(new HackDownloadSpeed());
		subCommands.add(new HackUploadSpeed());
		subCommands.add(new HackUploads());
		subCommands.add(new HackCategory());
		subCommands.add(new HackTag());

	}

	@Override
	public String getCommandDescriptions()
	{
		return "hack [<various options>]\t#\tModify torrent settings. Use without parameters for further help.";
	}

	@Override
	public void printHelpExtra(PrintStream out, List args) {
		out.println("> -----");
		out.println("'hack' syntax:");
		if( args.size() > 0 ) {
			String command = (String) args.remove(0);
			IConsoleCommand cmd = subCommands.get(command);
			if( cmd != null )
				cmd.printHelp(out, args);
			return;
		}
		out.println("hack <torrent id> <command> <command options>");
		out.println();
		out.println("<torrent id> can be one of the following:");
		out.println("<#>\t\tNumber of a torrent. You have to use 'show torrents' first as the number is taken from there.");
		out.println("hash <hash>\tApplied to torrent with the hash <hash> as given in the xml output or extended torrent info ('show <#>').");
		out.println("help\t\tDetailed help for <command>");
		out.println();
		out.println("Available <command>s:");
		for (Iterator iter = subCommands.iterator(); iter.hasNext();) {
			TorrentSubCommand cmd = (TorrentSubCommand) iter.next();
			out.println(cmd.getCommandDescriptions());
		}
		out.println("> -----");
	}

	/**
	 * finds the appropriate subcommand and executes it.
	 * the execute() method will have taken care of finding/iterating over the
	 * appropriate torrents
	 */
	@Override
	protected boolean performCommand(ConsoleInput ci, DownloadManager dm,
	                                 List args)
	{
		if (args.isEmpty()) {
			ci.out.println("> Not enough parameters for command '" + getCommandName() + "'.");
			return false;
		}
		String subCommandName = (String)args.remove(0);
		TorrentSubCommand cmd = (TorrentSubCommand) subCommands.get(subCommandName);
		if( cmd != null )
			return cmd.performCommand(ci, dm, args);
		else
		{
			ci.out.println("> Command 'hack': Command parameter '" + subCommandName + "' unknown.");
			return false;
		}
	}

	private static class HackDownloadSpeed extends TorrentSubCommand
	{
		public HackDownloadSpeed()
		{
			super("downloadspeed", "d");
		}

		@Override
		public String getCommandDescriptions() {
			return "downloadspeed\td\tSet max download speed [in kbps]of a torrent (0 for unlimited).";
		}

		/**
		 * locate the appropriate subcommand and execute it
		 */
		@Override
		public boolean performCommand(ConsoleInput ci, DownloadManager dm, List args)
		{
			if (args.isEmpty()) {
				ci.out.println("> Command 'hack': Not enough parameters for subcommand '" + getCommandName() + "'");
				return false;
			}
			int newSpeed = Math.max(-1, Integer.parseInt((String) args.get(0)));
			dm.getStats().setDownloadRateLimitBytesPerSecond(newSpeed*DisplayFormatters.getKinB());
			return true;
		}
	}

	private static class HackUploadSpeed extends TorrentSubCommand
	{
		public HackUploadSpeed()
		{
			super("uploadspeed", "u");
		}

		@Override
		public String getCommandDescriptions() {
			return "uploadspeed\tu\tSet max upload speed [in kbps] of a torrent (0 for unlimited).";
		}

		/**
		 * locate the appropriate subcommand and execute it
		 */
		@Override
		public boolean performCommand(ConsoleInput ci, DownloadManager dm, List args)
		{
			if (args.isEmpty()) {
				ci.out.println("> Command 'hack': Not enough parameters for subcommand '" + getCommandName() + "'");
				return false;
			}
			int newSpeed = Math.max(-1, Integer.parseInt((String) args.get(0)));
			dm.getStats().setUploadRateLimitBytesPerSecond(newSpeed*DisplayFormatters.getKinB());
			return true;
		}
	}

	private static class HackUploads extends TorrentSubCommand
	{
		public HackUploads()
		{
			super("uploads", "v");
		}

		@Override
		public String getCommandDescriptions() {
			return "uploads\tv\tSet max upload slots of a torrent.";
		}

		/**
		 * locate the appropriate subcommand and execute it
		 */
		@Override
		public boolean performCommand(ConsoleInput ci, DownloadManager dm, List args)
		{
			if (args.isEmpty()) {
				ci.out.println("> Command 'hack': Not enough parameters for subcommand '" + getCommandName() + "'");
				return false;
			}
			int newSlots = Math.max(-1, Integer.parseInt((String) args.get(0)));
			dm.setMaxUploads(newSlots);
			return true;
		}
	}

	private static class HackTracker extends TorrentSubCommand
	{
		private final CommandCollection subCommands = new CommandCollection();

		public HackTracker()
		{
			super("tracker", "t");
			subCommands.add(new HackHost());
			subCommands.add(new HackPort());
			subCommands.add(new HackURL());
		}

		@Override
		public void printHelpExtra(PrintStream out, List args)
		{
			out.println("hack <torrent id> tracker [command] <new value>");
			out.println();
			out.println("[command] can be one of the following:");
			for (Iterator iter = subCommands.iterator(); iter.hasNext();) {
				TorrentSubCommand cmd = (TorrentSubCommand) iter.next();
				out.println(cmd.getCommandDescriptions());
			}
			out.println();
			out.println("You can also omit [command] and only give a new full URL (just like the [command] 'url').");
			out.println("> -----");
		}

		/**
		 * locate the appropriate subcommand and execute it
		 */
		@Override
		public boolean performCommand(ConsoleInput ci, DownloadManager dm, List args)
		{
			if (args.isEmpty()) {
				ci.out.println("> Command 'hack': Not enough parameters for subcommand '" + getCommandName() + "'");
				return false;
			}
			String trackercommand = (String) args.remove(0);
			TRTrackerAnnouncer client = dm.getTrackerClient();
			//ci.out.println("> Command 'hack': Debug: '"+trackercommand+"'");
			if (client == null) {
				ci.out.println("> Command 'hack': Tracker interface not available.");
				return false;
			}
			TorrentSubCommand cmd = (TorrentSubCommand) subCommands.get(trackercommand);
			if( cmd == null )
			{
				args.add(trackercommand);
				cmd = (TorrentSubCommand) subCommands.get("url");
			}

			return cmd.performCommand(ci, dm, args);
		}

		@Override
		public String getCommandDescriptions() {
			return "tracker\t\tt\tModify Tracker URL of a torrent.";
		}
	}

	private static class HackFile extends TorrentSubCommand
	{
		public HackFile()
		{
			super("file", "f");
		}
		@Override
		public void printHelpExtra(PrintStream out, List args)
		{
			out.println("hack <torrent id> file <#> <priority>");
			out.println();
			out.println("<#> Number of the file.");
			out.println();
			out.println("<priority> can be one of the following:");
			out.println("normal\t\tn\tNormal Priority");
			out.println("high\t\th|+\tHigh Priority");
			out.println("nodownload\t!|-\tDon't download this file.");
			out.println("> -----");
		}
		@Override
		public boolean performCommand(ConsoleInput ci, DownloadManager dm, List args)
		{
			if (args.size() < 2) {
				ci.out.println("> Command 'hack': Not enough parameters for subcommand 'file'.");
				return false;
			}
			try {
				DiskManager disk = dm.getDiskManager();
				DiskManagerFileInfo files[] = disk.getFiles();
				int file = Integer.parseInt((String) args.get(0));
				String c = (String) args.get(1);
				if (c.equalsIgnoreCase("normal") || c.equalsIgnoreCase("n")) {
					files[file - 1].setSkipped(false);
					files[file - 1].setPriority(0);
					ci.out.println("> Set file '"+files[file - 1].getFile(true).getName()+"' to normal priority.");
				} else if (c.equalsIgnoreCase("high") || c.equalsIgnoreCase("h") || c.equalsIgnoreCase("+")) {
					files[file - 1].setSkipped(false);
					files[file - 1].setPriority(1);
					ci.out.println("> Set file '"+files[file - 1].getFile(true).getName()+"' to high priority.");
				} else if (c.equalsIgnoreCase("nodownload") || c.equalsIgnoreCase("!") || c.equalsIgnoreCase("-")) {
					files[file - 1].setSkipped(true);
					files[file - 1].setPriority(0);
					ci.out.println("> Stopped to download file '"+files[file - 1].getFile(true).getName()+"'.");
				} else {
					ci.out.println("> Command 'hack': Unknown priority '" + c + "' for command parameter 'file'.");
					return false;
				}
				return true;
			} catch (Exception e) {
				ci.out.println("> Command 'hack': Exception while executing subcommand 'file': " + e.getMessage());
				return false;
			}
		}

		@Override
		public String getCommandDescriptions() {
			return "file\t\tf\tModify priority of a single file of a batch torrent.";
		}
	}

	private static class HackPort extends TorrentSubCommand
	{
		public HackPort()
		{
			super("port", "p");
		}
		@Override
		public boolean performCommand(ConsoleInput ci, DownloadManager dm, List args)
		{
			if (args.isEmpty()) {
				ci.out.println("> Command 'hack': Not enough parameters for subcommand parameter 'port'.");
				return false;
			}
			TRTrackerAnnouncer client = dm.getTrackerClient();
			try {
				URI uold = new URI(client.getTrackerURL().toString());
				String portStr = (String) args.get(0);
				URI unew = new URI(uold.getScheme(), uold.getUserInfo(), uold.getHost(), Integer.parseInt(portStr), uold.getPath(), uold.getQuery(), uold.getFragment());
				client.setTrackerURL(new URL(unew.toString()));
				ci.out.println("> Set Tracker URL for '"+dm.getSaveLocation()+"' to '"+unew.toString()+"'");
			} catch (Exception e) {
				ci.out.println("> Command 'hack': Assembling new tracker url failed: "+e.getMessage());
				return false;
			}
			return true;
		}
		@Override
		public String getCommandDescriptions() {
			return "port\t\tp\tChange the port.";
		}
	}
	private static class HackHost extends TorrentSubCommand
	{
		public HackHost()
		{
			super("host", "h");
		}
		@Override
		public boolean performCommand(ConsoleInput ci, DownloadManager dm, List args)
		{
			if (args.isEmpty()) {
				ci.out.println("> Command 'hack': Not enough parameters for subcommand parameter 'host'.");
				return false;
			}
			TRTrackerAnnouncer client = dm.getTrackerClient();
			try {
				URI uold = new URI(client.getTrackerURL().toString());
				URI unew = new URI(uold.getScheme(), uold.getUserInfo(), (String)args.get(0), uold.getPort(), uold.getPath(), uold.getQuery(), uold.getFragment());
				client.setTrackerURL(new URL(unew.toString()));
				ci.out.println("> Set Tracker URL for '"+dm.getSaveLocation()+"' to '"+unew.toString()+"'");
			} catch (Exception e) {
				ci.out.println("> Command 'hack': Assembling new tracker url failed: "+e.getMessage());
				return false;
			}
			return true;
		}
		@Override
		public String getCommandDescriptions() {
			return "host\t\th\tChange the host.";
		}
	}
	private static class HackURL extends TorrentSubCommand
	{
		public HackURL()
		{
			super("url", "u");
		}
		@Override
		public boolean performCommand(ConsoleInput ci, DownloadManager dm, List args)
		{
			if (args.isEmpty()) {
				ci.out.println("> Command 'hack': Not enough parameters for subcommand parameter 'url'.");
				return false;
			}
			TRTrackerAnnouncer client = dm.getTrackerClient();

			try {
				String uriStr = (String) args.get(0);
				URI uri = new URI(uriStr);
				client.setTrackerURL(new URL(uri.toString()));
				ci.out.println("> Set Tracker URL for '"+dm.getSaveLocation()+"' to '"+uri+"'");
			} catch (Exception e) {
				ci.out.println("> Command 'hack': Parsing tracker url failed: "+e.getMessage());
				return false;
			}
			return true;
		}
		@Override
		public String getCommandDescriptions() {
			return "url\t\tu\tChange the full URL (Note: you have to include the '/announce' part).";
		}
	}

	private static class HackCategory extends TorrentSubCommand
	{
		public HackCategory()
		{
			super("category", "cat");
		}

		@Override
		public String getCommandDescriptions() {
			return "category [set <category_name>|clear]\t\tcat\tSet or clear the torrent's. Category will be created if necessary.";
		}

		/**
		 * locate the appropriate subcommand and execute it
		 */
		@Override
		public boolean performCommand(ConsoleInput ci, DownloadManager dm, List<String> args)
		{
			if (args.size() < 1) {
				ci.out.println("> Command 'hack': Not enough parameters for subcommand '" + getCommandName() + "'");
				return false;
			}

			String op = args.get(0);

			if ( op.equals( "set" )){

				if (args.size() < 2) {
					ci.out.println("> Command 'hack': Not enough parameters for subcommand '" + getCommandName() + "'");
					return false;
				}

				String cat_name = args.get(1);

				Category cat = CategoryManager.getCategory( cat_name );

				if ( cat == null ){

					cat = CategoryManager.createCategory( cat_name );
				}

				dm.getDownloadState().setCategory( cat );

			}else if ( op.equals( "clear" )){

				dm.getDownloadState().setCategory( null );
			}

			return true;
		}
	}

	private static class HackTag extends TorrentSubCommand
	{
		public HackTag()
		{
			super("tag", "tag");
		}

		@Override
		public String getCommandDescriptions() {
			return "tag [add|remove] <tag_name>\t\tAdd or remove a tag. Tag will be created if necessary.";
		}

		/**
		 * locate the appropriate subcommand and execute it
		 */
		@Override
		public boolean performCommand(ConsoleInput ci, DownloadManager dm, List<String> args)
		{
			if (args.size() < 2) {
				ci.out.println("> Command 'hack': Not enough parameters for subcommand '" + getCommandName() + "'");
				return false;
			}

			try{
				String op = args.get(0);
				String tag_name	= args.get(1);

				TagManager tm = TagManagerFactory.getTagManager();

				TagType tt = tm.getTagType( TagType.TT_DOWNLOAD_MANUAL );

				Tag tag = tt.getTag( tag_name, true );

				if ( op.equals( "add" )){

					if ( tag == null ){

						tag = tt.createTag( tag_name, true );

						ci.out.println( "Tag '" + tag_name + "' created" );
					}

					tag.addTaggable( dm );

				}else if ( op.equals( "remove" )){

					if ( tag == null ){

						ci.out.println("Tag '" + tag_name + "' not found" );

					}else{

						tag.removeTaggable( dm );
					}
				}else{

					ci.out.println("> Command 'hack': Invalid parameters for '" + getCommandName() + "'");
					return false;
				}
			}catch( Throwable e ){

				ci.out.println( "Command failed: " + Debug.getNestedExceptionMessage(e));

				return false;
			}

			return true;
		}
	}
}
