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

package com.biglybt.platform.unix;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.FileUtil;
import com.biglybt.core.util.SystemProperties;
import com.biglybt.pif.Plugin;
import com.biglybt.pif.PluginException;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.ui.UIInstance;
import com.biglybt.pif.ui.UIManagerListener;
import com.biglybt.pif.update.UpdateInstaller;
import com.biglybt.pif.update.UpdateManager;
import com.biglybt.pifimpl.local.PluginInitializer;
import com.biglybt.platform.PlatformManager;
import com.biglybt.platform.PlatformManagerCapabilities;
import com.biglybt.platform.PlatformManagerFactory;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.UserPrompterResultListener;
import com.biglybt.update.UpdaterUtils;

/**
 * @author TuxPaper
 * @created Jul 24, 2007
 *
 */
public class PlatformManagerUnixPlugin
	implements Plugin
{
	private PluginInterface plugin_interface;

	// @see com.biglybt.pif.Plugin#initialize(com.biglybt.pif.PluginInterface)
	@Override
	public void initialize(PluginInterface _plugin_interface)
			throws PluginException {
		plugin_interface = _plugin_interface;

		plugin_interface.getPluginProperties().setProperty("plugin.name",
				"Platform-Specific Support");

		String version = "1.0"; // default version if plugin not present

		PlatformManager platform = PlatformManagerFactory.getPlatformManager();

		if (platform.hasCapability(PlatformManagerCapabilities.GetVersion)) {

			try {
				version = platform.getVersion();

			} catch (Throwable e) {

				Debug.printStackTrace(e);
			}

		} else {

			plugin_interface.getPluginProperties().setProperty("plugin.version.info",
					"Not required for this platform");

		}

		plugin_interface.getPluginProperties().setProperty("plugin.version",
				version);

  		plugin_interface.getUIManager().addUIListener(new UIManagerListener() {
  			boolean done = false;

  			@Override
			  public void UIDetached(UIInstance instance) {
  			}

  			@Override
			  public void UIAttached(UIInstance instance) {
  				if (!done){

  					done = true;

  					if (Constants.compareVersions(UpdaterUtils.getUpdaterPluginVersion(),"1.8.5") >= 0) {

  						checkStartupScript();
  					}
  				}
  			}
  		});
	}

	/**
	 *
	 *
	 * @since 3.0.1.7
	 */
	private void checkStartupScript() {
		COConfigurationManager.setIntDefault("unix.script.lastaskversion", -1);
		int lastAskedVersion = COConfigurationManager.getIntParameter("unix.script.lastaskversion");

		String sVersion = System.getProperty(SystemProperties.SYSPROP_SCRIPT_VERSION, "0");
		int version = 0;
		try {
			version = Integer.parseInt(sVersion);
		} catch (Throwable t) {
		}

		Pattern pat = Pattern.compile("SCRIPT_VERSION=([0-9]+)",
				Pattern.CASE_INSENSITIVE);


		File oldFilePath;
		String sScriptFile = System.getProperty(SystemProperties.SYSPROP_APP_SCRIPT, null);
		if (sScriptFile != null && new File(sScriptFile).exists()) {
			oldFilePath = new File(sScriptFile);
		} else {
			oldFilePath = new File(SystemProperties.getApplicationPath(),
					Constants.APP_NAME.toLowerCase());
			if (!oldFilePath.exists()) {
				return;
			}
		}

		final String oldFilePathString = oldFilePath.getAbsolutePath();

		String oldStartupScript;
		try {
			oldStartupScript = FileUtil.readFileAsString(oldFilePath,
					65535, "utf8");
		} catch (IOException e) {
			oldStartupScript = "";
		}

		// Case: Script with no version, we update it, user selects restart.
		//       Restart doesn't include azureus.script.version yet, so we
		//       would normally prompt again.  This fix reads the version
		//       from the file if we don't have a version yet, thus preventing
		//       the second restart message
		if (version == 0) {
			Matcher matcher = pat.matcher(oldStartupScript);
			if (matcher.find()) {
				String sScriptVersion = matcher.group(1);
				try {
					version = Integer.parseInt(sScriptVersion);
				} catch (Throwable t) {
				}
			}
		}

		if (version <= lastAskedVersion) {
			return;
		}

		InputStream stream = getClass().getResourceAsStream("startupScript");
		try {
			String startupScript = FileUtil.readInputStreamAsString(stream, 65535,
					"utf8");
			Matcher matcher = pat.matcher(startupScript);
			if (matcher.find()) {
				String sScriptVersion = matcher.group(1);
				int latestVersion = 0;
				try {
					latestVersion = Integer.parseInt(sScriptVersion);
				} catch (Throwable t) {
				}
				if (latestVersion > version) {
					boolean autoUpdateScript = oldStartupScript.indexOf("AUTOUPDATE_SCRIPT=1") > 0;
					boolean bInformUserManual = true;

					if (autoUpdateScript) {
						if (version >= 1) {
							UpdateManager um = PluginInitializer.getDefaultInterface().getUpdateManager();
							UpdateInstaller installer = um.createInstaller();

							installer.addResource("biglybt.new", new ByteArrayInputStream(startupScript.getBytes()));
							String target = new File(installer.getInstallDir(), "biglybt").getAbsolutePath();
							installer.addMoveAction("biglybt.new", target);
							installer.addChangeRightsAction("+x", target);

							bInformUserManual = false;
						} else {
							// overwrite!
							try {
								FileUtil.writeBytesAsFile(oldFilePathString,
										startupScript.getBytes());
								Runtime.getRuntime().exec(new String[] {
									findCommand( "chmod" ),
									"+x",
									oldStartupScript
								});

								bInformUserManual = false;
							} catch (Throwable t) {
							}
						}
					}

					if (bInformUserManual) {
						final String newFilePath = new File(
								SystemProperties.getApplicationPath(), "biglybt.new").getAbsolutePath();
						FileUtil.writeBytesAsFile(newFilePath, startupScript.getBytes());
						showScriptManualUpdateDialog(newFilePath, oldFilePathString,
								latestVersion);
					} else {
						showScriptAutoUpdateDialog();
					}
				}
			}

		} catch (Throwable t) {
			t.printStackTrace();
		}finally{
			try{
				stream.close();
			}catch( Throwable e){
			}
		}
	}

	  private String
	  findCommand(
		String	name )
	  {
		final String[]  locations = { "/bin", "/usr/bin" };

		for ( String s: locations ){

			File f = new File( s, name );

			if ( f.exists() && f.canRead()){

				return( f.getAbsolutePath());
			}
		}

		return( name );
	  }

	private void showScriptManualUpdateDialog(String newFilePath,
			String oldFilePath, final int version) {
		final UIFunctions uif = UIFunctionsManager.getUIFunctions();
		if (uif != null) {
			final String sCopyLine = "cp \"" + newFilePath + "\" \"" + oldFilePath + "\"";
			uif.promptUser(
					MessageText.getString("unix.script.new.title"),
					MessageText.getString("unix.script.new.text", new String[] {
						newFilePath,
						sCopyLine
					}), new String[] {
						MessageText.getString("unix.script.new.button.quit"),
						MessageText.getString("unix.script.new.button.continue"),
						MessageText.getString("unix.script.new.button.asknomore"),
					}, 0, null, null, false, 0, new UserPrompterResultListener() {
						@Override
						public void prompterClosed(int answer) {
							if (answer == 0) {
								System.out.println("The line you should run:\n" + sCopyLine);
								uif.dispose(false);
							} else if (answer == 2) {
								COConfigurationManager.setParameter("unix.script.lastaskversion",
										version);
							}
						}
					});
		} else {
			System.out.println("NO UIF");
		}
	}

	private void showScriptAutoUpdateDialog() {
		final UIFunctions uif = UIFunctionsManager.getUIFunctions();
		if (uif != null) {
			uif.promptUser(MessageText.getString("unix.script.new.auto.title"),
					MessageText.getString("unix.script.new.auto.text", new String[] {}),
					new String[] {
						MessageText.getString("UpdateWindow.restart"),
						MessageText.getString("UpdateWindow.restartLater"),
					}, 0, null, null, false, 0, new UserPrompterResultListener() {
						@Override
						public void prompterClosed(int answer) {
							if (answer == 0) {
								uif.dispose(true);
							}
						}
					});
		} else {
			System.out.println("NO UIF");
		}
	}
}
