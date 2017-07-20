/*
 * Written and copyright 2001-2004 Tobias Minich. Distributed under the GNU
 * General Public License; see the README file. This code comes with NO
 * WARRANTY.
 *
 */

package com.biglybt.ui.console.commands;

import java.io.PrintStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.biglybt.core.disk.DiskManagerFileInfo;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.tracker.client.TRTrackerScraperResponse;
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.ui.console.ConsoleInput;

/**
 * base interface for all console commands
 * @author Tobias Minich
 */
public abstract class IConsoleCommand {
	protected static final class TorrentComparator implements Comparator<DownloadManager> {
			@Override
			public final int compare(DownloadManager aDL, DownloadManager bDL) {
				boolean aIsComplete = aDL.getStats().getDownloadCompleted(false) == 1000;
				boolean bIsComplete = bDL.getStats().getDownloadCompleted(false) == 1000;
				if (aIsComplete && !bIsComplete)
					return 1;
				if (!aIsComplete && bIsComplete)
					return -1;
				return aDL.getPosition() - bDL.getPosition();
			}
		}
	private String main_name;
	private String short_name;
	private HashSet commands;

	public IConsoleCommand(String main_name) {
		this(main_name, null);
	}

	public IConsoleCommand(String main_name, String short_name) {
		this.commands = new HashSet();
		this.main_name = main_name;
		this.short_name = short_name;

		if (main_name != null)  {commands.add(main_name);}
		if (short_name != null) {commands.add(short_name);}
	}

	/**
	 * execute the command with the specified name using the specified arguments
	 * @param commandName
	 * @param console
	 * @param arguments
	 */
	public abstract void execute(String commandName, ConsoleInput console, List<String> arguments);

	/**
	 * return high-level help about the commands supported by this object.
	 * @return
	 */
	public abstract String getCommandDescriptions();

	/**
	 * do nothing by default
	 * @param out
	 * @param args
	 */
	public final void printHelp(PrintStream out, List<String> args)
	{
		out.println(getCommandDescriptions());
		printHelpExtra(out, args);
	}

	public void printHelpExtra(PrintStream out, List<String> args) {
		// Do nothing by default.
	}

	/**
	 * helper method if subclasses want to print out help for a particular subcommand
	 * @param out
	 * @param arg
	 */
	protected final void printHelp(PrintStream out, String arg)
	{
		List args;
		if( arg != null )
		{
			args = new ArrayList();
			args.add(arg);
		}
		else
			args = Collections.EMPTY_LIST;

		printHelp(out, args);
	}

	/**
	 * returns the set of command names that this command understands.
	 * eg: the 'quit' command might understand 'quit', 'q', 'bye'
	 * other commands might actually have several command names and
	 * execute different code depending upon the command name
	 * @return
	 */
	public Set getCommandNames()
	{
		return Collections.unmodifiableSet(commands);
	}

	public final String getCommandName() {return this.main_name;}
	public final String getShortCommandName() {return this.short_name;}


	/**
	 * returns the summary details for the specified torrent. - we do this by obtaining
	 * the summary format and then performing variable substitution
	 * NOTE: we currently reprocess
	 * the summary format string each time however we could pre-parse this once.. its
	 * probably not that important though.
	 * @return
	 */
	protected String getTorrentSummary(DownloadManager dm) {
		StringBuilder tstate = new StringBuilder();
		String summaryFormat = getDefaultSummaryFormat();
		char lastch = '0';
		char []summaryChars = summaryFormat.toCharArray();
		for (int i = 0; i < summaryChars.length; i++) {
			char ch = summaryChars[i];
			if( ch == '%' && lastch != '\\' )
			{
				i++;
				if( i >= summaryChars.length )
					tstate.append('%');
				else
					tstate.append(expandVariable(summaryChars[i], dm));
			}
			else
				tstate.append(ch);

			lastch = ch;
		}
		return tstate.toString();
	}

	/**
	 * expands the specified variable character into a string. <br>currently available
	 * variables that can be expanded are:<br>
	 * <hr>
	 * %a for state<br>
	 * %c percentage complete<br>
	 * %t torrent details - error message if error, otherwise torrent name<br>
	 * %z size<br>
	 * %e ETA<br>
	 * %r progress, if we have disabled some files<br>
	 * %d download speed<br>
	 * %u upload speed<br>
	 * %D amount downloaded<br>
	 * %U amount uploaded<br>
	 * %v upload slots
	 * %s connected seeds<br>
	 * %p connected peers<br>
	 * %S tracker seeds<br>
	 * %P tracker peers<br>
	 * @param variable variable character, eg: 'e' for ETA
	 * @param dm download manager object
	 * @return string expansion of the variable
	 */
	protected String expandVariable( char variable, DownloadManager dm )
	{
		switch( variable )
		{
			case 'a':
				return getShortStateString(dm.getState());
			case 'c':
				DecimalFormat df = new DecimalFormat("000.0%");
				return df.format(dm.getStats().getCompleted() / 1000.0);
			case 't':
				if (dm.getState() == DownloadManager.STATE_ERROR)
					return dm.getErrorDetails();
				else {
					if (dm.getDisplayName() == null)
						return "?";
					else
						return dm.getDisplayName();
				}
			case 'z':
				return DisplayFormatters.formatByteCountToKiBEtc(dm.getSize());
			case 'e':
				return DisplayFormatters.formatETA(dm.getStats().getSmoothedETA());
			case 'r':
				long to = 0;
				long tot = 0;
				if (dm.getDiskManager() != null) {
					DiskManagerFileInfo files[] = dm.getDiskManager().getFiles();
					if (files != null) {
						if (files.length>1) {
							int c=0;
							for (int i = 0; i < files.length; i++) {
								if (files[i] != null) {
									if (!files[i].isSkipped()) {
										c += 1;
										tot += files[i].getLength();
										to += files[i].getDownloaded();
									}
								}
							}
							if (c == files.length)
								tot = 0;
						}
					}
				}
				DecimalFormat df1 = new DecimalFormat("000.0%");
				if (tot > 0) {
					return "      ("+df1.format(to * 1.0 / tot)+")";
				} else
					return "\t";
			case 'd':
				return DisplayFormatters.formatByteCountToKiBEtcPerSec(dm.getStats().getDataReceiveRate());
			case 'u':
				return DisplayFormatters.formatByteCountToKiBEtcPerSec(dm.getStats().getDataSendRate());
			case 'D':
				return DisplayFormatters.formatDownloaded(dm.getStats());
			case 'U':
				return DisplayFormatters.formatByteCountToKiBEtc(dm.getStats().getTotalDataBytesSent());
			case 's':
				return Integer.toString(dm.getNbSeeds());
			case 'p':
				return Integer.toString(dm.getNbPeers());
			case 'v':
				return Integer.toString(dm.getMaxUploads());
			case 'I':
				int downloadSpeed = dm.getStats().getDownloadRateLimitBytesPerSecond();
				if( downloadSpeed <= 0 )
					return "";
				return "(max " + DisplayFormatters.formatByteCountToKiBEtcPerSec(downloadSpeed) + ")";
			case 'O':
				int uploadSpeed = dm.getStats().getUploadRateLimitBytesPerSecond();
				if( uploadSpeed <= 0 )
					return "";
				return "(max " + DisplayFormatters.formatByteCountToKiBEtcPerSec(uploadSpeed) + ")";

			case 'S':
			case 'P':
				TRTrackerScraperResponse hd = dm.getTrackerScrapeResponse();
				if (hd == null || !hd.isValid())
					return "?";
				else
				{
					if( variable == 'S' )
						return Integer.toString(hd.getSeeds());
					else
						return Integer.toString(hd.getPeers());
				}
			default:
				return "??" + variable + "??";
		}
	}

	/**
	 * returns the format string (in printf style format) to use for displaying the torrent summary
	 * @return
	 */
	protected String getDefaultSummaryFormat()
	{
		return "[%a] %c\t%t (%z) ETA: %e\r\n%r\tSpeed: %d%I / %u%O\tAmount: %D / %U\tConnections: %s(%S) / %p(%P)";
	}

	/**
	 * returns a string representation of the specified state number
	 * suitable for inclusion in a torrent summary
	 * @param dmstate
	 * @return
	 */
	private static String getShortStateString(int dmstate) {
		switch( dmstate )
		{
		case DownloadManager.STATE_INITIALIZING:
			return("I");
		case DownloadManager.STATE_ALLOCATING:
			return("A");
		case DownloadManager.STATE_CHECKING:
			return("C");
		case DownloadManager.STATE_DOWNLOADING:
			return(">");
		case DownloadManager.STATE_ERROR:
			return("E");
		case DownloadManager.STATE_SEEDING:
			return("*");
		case DownloadManager.STATE_STOPPED:
			return("!");
		case DownloadManager.STATE_WAITING:
			return(".");
		case DownloadManager.STATE_READY:
			return(":");
		case DownloadManager.STATE_QUEUED:
			return("-");
		default:
			return("?");
		}
	}

}
