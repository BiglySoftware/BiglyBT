/*
 * Written and copyright 2001-2003 Tobias Minich. Distributed under the GNU
 * General Public License; see the README file. This code comes with NO
 * WARRANTY.
 *
 * AddFind.java
 *
 * Created on 23.03.2004
 *
 */
package com.biglybt.ui.console.commands;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.biglybt.ui.common.util.StringPattern;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.OptionBuilder;

import com.biglybt.core.CoreException;
import com.biglybt.ui.console.ConsoleInput;

/**
 * this class allows the user to add and find torrents.
 * when adding, you may specify an output directory
 * when finding, it will cache the files it finds into the ConsoleInput object
 * so that they can then be added by id
 * @author tobi, fatal
 */
public class AddFind extends OptionsConsoleCommand {

	public AddFind()
	{
		super("add", "a");

		OptionBuilder.withArgName("outputDir");
		OptionBuilder.withLongOpt("output");
		OptionBuilder.hasArg();
		OptionBuilder.withDescription("override default download directory");
		OptionBuilder.withType(File.class);
		getOptions().addOption( OptionBuilder.create('o') );
		getOptions().addOption("r", "recurse", false, "recurse sub-directories.");
		getOptions().addOption("f", "find", false, "only find files, don't add.");
		getOptions().addOption("h", "help", false, "display help about this command");
		getOptions().addOption("l", "list", false, "list previous find results");
	}

	@Override
	public String getCommandDescriptions()
	{
		return "add [addoptions] [.torrent path|url]\t\ta\tAdd a download from the given .torrent file path or url. Example: 'add /path/to/the.torrent' or 'add http://www.url.com/to/the.torrent'";
	}

	@Override
	public void execute(String commandName, ConsoleInput ci, CommandLine commands)
	{
		if( commands.hasOption('l') )
		{
			ci.out.println("> -----");
			showAdds(ci);
			ci.out.println("> -----");
			return;
		}
		else if( commands.hasOption('h') || commands.getArgs().length == 0 )
		{
			printHelp(ci.out, (String)null);
			return;
		}
		String outputDir = ".";
		if (commands.hasOption('o'))
			outputDir = commands.getOptionValue('o');
		else
			outputDir = ci.getDefaultSaveDirectory();

		File f = new File(outputDir);
		if( ! f.isAbsolute() )
		{
			// make it relative to current directory
			try {
				outputDir = new File(".", outputDir).getCanonicalPath();
			} catch (IOException e) {
				throw new CoreException("exception occurred while converting directory: ./" + outputDir + " to its canonical path");
			}
		}
		boolean scansubdir = commands.hasOption('r');
		boolean finding = commands.hasOption('f');

		String[] whatelse = commands.getArgs();
		for (int i = 0; i < whatelse.length; i++) {
			String arg = whatelse[i];
			try {
				// firstly check if it is a valid URL
				new URL(arg);
				addRemote(ci, arg, outputDir);
			} catch (MalformedURLException e)
			{
				// assume that it's a local file or file id from a previous find
				addLocal(ci, arg, outputDir, scansubdir, finding);
			}
		}
	}

	/**
	 * attempt to download the torrent specified by 'arg' and save the files
	 * in the torrent to the specified output directory
	 * @param ci
	 * @param arg URL of torrent to download
	 * @param outputDir directory to save files from torrent to
	 */
	protected void addRemote(ConsoleInput ci, String arg, String outputDir) {
		ci.out.println("> Starting Download of " + arg + " ...");
		try {
			ci.downloadRemoteTorrent(arg, outputDir);
		} catch (Exception e) {
			ci.out.println("An error occurred while downloading torrent: " + e.getMessage());
			e.printStackTrace(ci.out);
		}
	}

	/**
	 * attempt a local add (arg may be a directory, a file or a pattern eg: d:/*.torrent)
	 * @param ci
	 * @param arg argument - could be directory, file or pattern eg: d:\*.torrent
	 * @param outputDir directory to save files from torrent to
	 * @param scansubdir if true, will recurse subdirectories looking for files to add
	 * @param finding if true, don't start downloading the files; simply add them to the 'found' list
	 */
	protected void addLocal(ConsoleInput ci, String arg, String outputDir, boolean scansubdir, boolean finding)
	{
		// substitute ~ for home directory, if specified
		arg = transformLocalArgument(arg);
		// see if the argument is an existing file or directory
		File test = new File(arg);
		if (test.exists()) {
			if (test.isDirectory()) {
				List<File> toAdd = new ArrayList<>();
				try {
					Stream<Path> stream = scansubdir?Files.walk(test.toPath()):Files.list(test.toPath());
					
					toAdd = stream.filter( p->
								{
									String str = p.toString();
						
									return( str.endsWith(".torrent") || str.endsWith(".tor"));
						
								}).map(Path::toFile).collect( Collectors.toList());	
				
				} catch (IOException e) {
				}
				if (toAdd.size() > 0) {
					addFiles( ci, toAdd.toArray(new File[toAdd.size()]), finding, outputDir );
				} else {
					ci.adds = null;
					ci.out.println("> Directory '" + arg + "' seems to contain no torrent files.");
				}
			} else {
				ci.downloadTorrent(arg, outputDir);
				ci.out.println("> '" + arg + "' added.");
				ci.torrents.clear();
			}
			return;
		}

		// check to see if they are numeric and if so, try and add them from the 'adds' in ci
		try {
			int id = Integer.parseInt(arg);
			if( ci.adds != null && ci.adds.length > id )
			{
				String torrentPath = ci.adds[id].getAbsolutePath();
				ci.downloadTorrent(torrentPath, outputDir);
				ci.out.println("> '" + torrentPath + "' added.");
				ci.torrents.clear();
			}
			else
			{
				ci.out.println("> No such file id '" + id + "'. Try \"add -l\" to list available files");
			}
			return;
		} catch (NumberFormatException e)
		{
		}
		// last resort - try to process it as a directory/pattern eg: c:/torrents/*.torrent
		String dirName = test.getParent();
		if( dirName == null )
			dirName = ".";
		final String filePattern = test.getName();
		List<File> files = new ArrayList<>();
		try {
			files = Files.list(Paths.get(dirName)).filter(
							p->new StringPattern(p.toString()).matches(filePattern)
						).map(Path::toFile).collect( Collectors.toList());	
		} catch (IOException e) {
		}

		if (files.size() > 0) {
			addFiles(ci, files.toArray(new File[files.size()]), finding, outputDir );
		} else {
			ci.adds = null;
			ci.out.println("> No files found. Searched for '" + filePattern + "' in '" + dirName + "'");
		}
	}
	/**
	 * perform any transformations on the argument - in this case we are
	 * replacing '~' with the user's home directory.
	 * @param arg
	 * @return
	 */
	protected String transformLocalArgument(String arg) {
		if( arg.startsWith("~/") || arg.equals("~") )
		{
		    arg = arg.replace("~", System.getProperty("user.home"));
		}
		return arg;
	}

	/**
	 * if finding is set, just print the available files and add them to the 'add' list inside the consoleinput object,
	 * otherwise actually add the torrents, saving to the specified output directory
	 * @param toadd
	 * @param finding
	 * @param outputDir
	 */
	protected void addFiles(ConsoleInput ci, File[] toadd, boolean finding, String outputDir) {
		ci.out.println("> -----");
		ci.out.println("> Found " + toadd.length + " files:");

		if( finding )
		{
			ci.adds = toadd;
			showAdds(ci);
		}
		else
		{
			for (int i = 0; i < toadd.length; i++) {
				ci.downloadTorrent(toadd[i].getAbsolutePath(), outputDir);
				ci.out.println("> '" + toadd[i].getAbsolutePath() + "' added.");
				ci.torrents.clear();
			}
		}
		ci.out.println("> -----");
	}

	/**
	 * prints out the files in the 'add' list that is stored in the console input object.
	 * @param ci
	 */
	private void showAdds(ConsoleInput ci) {
		if( ci.adds == null || ci.adds.length == 0 )
		{
			ci.out.println("> No files found. Try \"add -f <path>\" first");
			return;
		}
		for (int i = 0; i < ci.adds.length; i++) {
			ci.out.print(">\t" + i + ":\t");
			try {
				ci.out.println(ci.adds[i].getCanonicalPath());
			} catch (Exception e) {
				ci.out.println(ci.adds[i].getAbsolutePath());
			}
		}
		ci.out.println("> To add, simply type 'add <id>'");
	}
}
