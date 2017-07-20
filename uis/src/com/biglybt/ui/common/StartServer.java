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

package com.biglybt.ui.common;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;

import com.biglybt.core.impl.CoreSingleInstanceClient;
import com.biglybt.core.util.Constants;

/**
 * @author Olivier
 *
 */
public class StartServer extends Thread {

	private ServerSocket socket;

	private int state;

	private boolean bContinue;

	public static final int STATE_FAULTY = 0;

	public static final int STATE_LISTENING = 1;

	public StartServer() {
		super("Start Server");

		try {
			socket = new ServerSocket(Constants.INSTANCE_PORT, 50,
					InetAddress.getByName("127.0.0.1"));
			state = STATE_LISTENING;
		} catch (Throwable t) {

			// DON'T USE LOGGER here as we DON't want to initialise all the logger stuff
			// and in particular AEDiagnostics config dirty stuff!!!!

			state = STATE_FAULTY;
			String reason = t.getMessage() == null ? "<>" : t.getMessage();

			if (!reason.contains("in use")) {
				System.out.println("StartServer ERROR: unable"
						+ " to bind to 127.0.0.1:" + Constants.INSTANCE_PORT + " listening"
						+ " for passed torrent info: " + reason);
			}
		}
	}

	@Override
	public void run() {
		bContinue = true;
		while (bContinue) {
			BufferedReader br = null;
			try {
				Socket sck = socket.accept();

				CoreSingleInstanceClient.sendReply(sck);

				String address = sck.getInetAddress().getHostAddress();
				if (!address.equals("localhost") && !address.equals("127.0.0.1")) {
					continue;
				}
				br = new BufferedReader(new InputStreamReader(sck.getInputStream(),
						"UTF-8"));
				String line = br.readLine();
				//System.out.println("received : " + line);
				if (line != null) {
					line = line.replaceAll("([^&]);", "$1\n");
					String[] args = line.split("\n");
					for (int i = 0; i < args.length; i++) {
						args[i] = args[i].replaceAll("&(.)", "$1");
					}
					if (args.length == 0
							|| !args[0].equals(CoreSingleInstanceClient.ACCESS_STRING)) {
						System.err.println("StartServer: Wrong access token.");
						continue;
					}

					if (args.length == 1 || !args[1].equals("args")) {
						System.err.println(
								"Something strange was sent to the StartServer: " + line);
						continue;
					}

					String[] realArgs = new String[args.length - 2];
					System.arraycopy(args, 2, realArgs, 0, realArgs.length);

					try {
						Options options = UIConst.buildOptions();
						CommandLine commands = UIConst.buildCommandLine(options,
								realArgs);

						if (commands.hasOption("u")) {
							String ui = commands.getOptionValue("u");
							if (!UIConst.UIS.containsKey(ui)) {
								PrintStream oldOut = System.out;
								try {
									System.setOut(new PrintStream(sck.getOutputStream()));
									UIConst.startUI(ui);
								} finally {
									System.setOut(oldOut);
								}

								options = UIConst.buildOptions();
								commands = UIConst.buildCommandLine(options,
										realArgs);
							}
						}

						// Special Exit if user ask for help
						if (commands != null && commands.hasOption('h')) {
							HelpFormatter hf = new HelpFormatter();
							hf.setOptionComparator(null);
							try {
								OutputStream os = sck.getOutputStream();
								PrintWriter pw = new PrintWriter(os);
								hf.printHelp(pw, hf.getWidth(),
										"[options] [torrent [torrent ...]]", null, options,
										hf.getLeftPadding(), hf.getDescPadding(), null, false);
								pw.flush();
							} catch (Exception e) {
								e.printStackTrace();
							}

						} else {

							String s = UIConst.processArgs(commands, options, realArgs);
							if (s.length() > 0) {
								try {
									OutputStream os = sck.getOutputStream();
									os.write(s.getBytes("utf8"));
									os.flush();
								} catch (Exception e) {
								}
							}
						}

					} catch (Exception e) {
						e.printStackTrace();
					}

				}

				sck.close();

			} catch (Exception e) {
				if (!(e instanceof SocketException))
					e.printStackTrace();
				bContinue = false;
			} finally {
				try {
					if (br != null)
						br.close();
				} catch (Exception e) { /*ignore */
				}
			}
		}
	}

	public void stopIt() {
		bContinue = false;
		try {
			if (socket != null) {
				socket.close();
			}
		} catch (Throwable e) {
			/*ignore */}
	}

	public int getServerState() {
		return state;
	}

}
