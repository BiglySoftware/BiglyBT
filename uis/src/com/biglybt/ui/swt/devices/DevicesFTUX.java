/*
 * Created on Mar 7, 2009
 *
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA
 */

package com.biglybt.ui.swt.devices;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.TraverseEvent;
import org.eclipse.swt.events.TraverseListener;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.CoreRunningListener;
import com.biglybt.core.devices.DeviceManager;
import com.biglybt.core.devices.DeviceManagerFactory;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.Wiki;
import com.biglybt.pif.PluginException;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.installer.InstallablePlugin;
import com.biglybt.pif.installer.PluginInstallationListener;
import com.biglybt.pif.installer.PluginInstaller;
import com.biglybt.pif.installer.StandardPlugin;
import com.biglybt.pif.update.UpdateCheckInstance;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.mdi.MdiEntry;
import com.biglybt.ui.mdi.MdiEntryVitalityImage;
import com.biglybt.ui.mdi.MultipleDocumentInterface;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.shell.ShellFactory;
import com.biglybt.ui.swt.shells.CoreWaiterSWT;
import com.biglybt.ui.swt.views.skin.sidebar.SideBar;

/**
 * @author TuxPaper
 * @created Mar 7, 2009
 *
 */
public class DevicesFTUX
{
	public static DevicesFTUX instance;

	private Shell shell;

	private Button checkITunes;

	private StyledText	text_area;
	
	private Composite install_area;

	private Composite install_area_parent;

	final List<Runnable>	to_fire_on_complete = new ArrayList<>();

	/**
	 *
	 * @since 4.1.0.5
	 */
	boolean isDisposed() {
		return shell.isDisposed();
	}

	/**
	 *
	 *
	 * @since 4.1.0.5
	 */
	void setFocus(Runnable fire_on_install) {

		synchronized( to_fire_on_complete ){

			to_fire_on_complete.add( fire_on_install );
		}
		shell.forceActive();
		shell.forceFocus();
	}

	void open(Runnable fire_on_install) {

		synchronized( to_fire_on_complete ){

			to_fire_on_complete.add( fire_on_install );
		}

		// This is a simple dialog box, so instead of using SkinnedDialog, we'll
		// just built it old school
		shell = ShellFactory.createMainShell(SWT.DIALOG_TRIM);
		shell.setText(MessageText.getString("devices.turnon.title"));

		Utils.setShellIcon(shell);

		text_area = new StyledText( shell, SWT.BORDER );
		text_area.setEditable( false );
		text_area.setLeftMargin( 5 );
		String blurb = MessageText.getString( "devices.turnon.info");
		text_area.setText( "\n" + blurb + "\n" );
		
		checkITunes = new Button(shell, SWT.CHECK);
		checkITunes.setSelection(true);
		Messages.setLanguageText(checkITunes, "devices.turnon.itunes");

		if ( Constants.isWindows || Constants.isOSX ){
			
			PluginInterface itunes_plugin = null;
			try {
				itunes_plugin = CoreFactory.getSingleton().getPluginManager().getPluginInterfaceByID(
						"azitunes", true);
	
			} catch (Throwable ignored) {
			}
			if (itunes_plugin != null && itunes_plugin.getPluginState().isOperational()) {
				checkITunes.setEnabled(false);
			}
		}else{
				// no itunes support on Linux
			
			checkITunes.setSelection( false );
			checkITunes.setEnabled( false );
		}
		
		Link lblLearnMore = new Link(shell, SWT.NONE);
		lblLearnMore.setText("<A HREF=\"" + Wiki.DEVICES_FAQ + "\">"
				+ MessageText.getString("label.learnmore") + "</A>");
		lblLearnMore.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				Utils.launch(Wiki.DEVICES_FAQ);
			}
		});

		Button btnInstall = new Button(shell, SWT.NONE);
		Messages.setLanguageText(btnInstall, "Button.turnon");
		btnInstall.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				doInstall(checkITunes.getSelection());
			}
		});

		shell.setDefaultButton(btnInstall);

		Button btnCancel = new Button(shell, SWT.NONE);
		Messages.setLanguageText(btnCancel, "Button.cancel");
		btnCancel.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				shell.dispose();
			}
		});

		shell.addTraverseListener(new TraverseListener() {
			@Override
			public void keyTraversed(TraverseEvent e) {
				if (e.detail == SWT.TRAVERSE_ESCAPE) {
					shell.dispose();
				}
			}
		});

		install_area_parent = new Composite(shell, SWT.NONE);
		install_area_parent.setLayout(new FormLayout());
		install_area_parent.setVisible(false);

		install_area = new Composite(install_area_parent, SWT.NONE);

		FormLayout formLayout = new FormLayout();
		formLayout.marginWidth = formLayout.marginHeight = 0;
		shell.setLayout(formLayout);
		FormData fd;

		fd = Utils.getFilledFormData();
		fd.top = new FormAttachment(0, +5);
		fd.bottom = new FormAttachment(btnInstall, -5, SWT.TOP);
		fd.left = new FormAttachment(0, +5);
		fd.right = new FormAttachment(100, -5);
		text_area.setLayoutData(fd);

		fd = new FormData();
		fd.bottom = new FormAttachment(100, -10);
		fd.right = new FormAttachment(100, -10);
		btnCancel.setLayoutData(fd);

		fd = new FormData();
		fd.bottom = new FormAttachment(100, -10);
		fd.right = new FormAttachment(btnCancel, -12);
		fd.left  = new FormAttachment(lblLearnMore, +12); //
		btnInstall.setLayoutData(fd);

		fd = new FormData();
		fd.bottom = new FormAttachment(100, -5);
		fd.left = new FormAttachment(0, 10);
		fd.right = new FormAttachment(btnInstall, -12);
		checkITunes.setLayoutData(fd);

		fd = new FormData();
		fd.top = new FormAttachment(checkITunes, 0, SWT.CENTER);
		fd.left = new FormAttachment(checkITunes, 5);
		lblLearnMore.setLayoutData(fd);

		fd = new FormData();
		fd.top = new FormAttachment(text_area, 0);
		fd.bottom = new FormAttachment(100, 0);
		fd.left = new FormAttachment(0, 0);
		fd.right = new FormAttachment(100, 0);
		install_area_parent.setLayoutData(fd);

		fd = new FormData();
		fd.height = btnInstall.computeSize(SWT.DEFAULT, SWT.DEFAULT).y;
		fd.bottom = new FormAttachment(100, -5);
		fd.left = new FormAttachment(0, 5);
		fd.right = new FormAttachment(100, -12);
		install_area.setLayoutData(fd);

		Utils.makeButtonsEqualWidth( Arrays.asList( btnInstall, btnCancel ));
		shell.pack();
		Utils.centreWindow(shell);

		btnInstall.setFocus();
		shell.open();
	}

	/**
	 * @since 4.1.0.5
	 */
	protected void doInstall(final boolean itunes) {
		CoreWaiterSWT.waitForCoreRunning(new CoreRunningListener() {
			@Override
			public void coreRunning(Core core) {
				_doInstall(core, itunes);
			}
		});
	}

	protected void _doInstall(Core core, boolean itunes) {

		List<InstallablePlugin> plugins = new ArrayList<>(2);

		final PluginInstaller installer = core.getPluginManager().getPluginInstaller();

		StandardPlugin vuze_plugin = null;

		try {
			vuze_plugin = installer.getStandardPlugin("vuzexcode");

		} catch (Throwable ignored) {
		}

		if (vuze_plugin != null && !vuze_plugin.isAlreadyInstalled()) {
			plugins.add(vuze_plugin);
		}

		if (itunes) {
			StandardPlugin itunes_plugin = null;

			try {
				itunes_plugin = installer.getStandardPlugin("azitunes");

			} catch (Throwable ignored) {
			}

			if (itunes_plugin != null && !itunes_plugin.isAlreadyInstalled()) {
				plugins.add(itunes_plugin);
			}
		}

		if (plugins.size() == 0) {
			close();
			return;
		}
		InstallablePlugin[] installablePlugins = plugins.toArray(new InstallablePlugin[0]);

		try {
			install_area_parent.setVisible(true);
			install_area_parent.moveAbove(null);

			Map<Integer, Object> properties = new HashMap<>();

			properties.put(UpdateCheckInstance.PT_UI_STYLE,
					UpdateCheckInstance.PT_UI_STYLE_SIMPLE);

			properties.put(UpdateCheckInstance.PT_UI_PARENT_SWT_COMPOSITE,
					install_area);

			properties.put(UpdateCheckInstance.PT_UI_DISABLE_ON_SUCCESS_SLIDEY, true);

			installer.install(installablePlugins, false, properties,
					new PluginInstallationListener() {
						@Override
						public void completed() {
							close();

							MultipleDocumentInterface mdi = UIFunctionsManager.getUIFunctions().getMDI();
							MdiEntry entry = mdi.getEntry(SideBar.SIDEBAR_HEADER_DEVICES);
							List<? extends MdiEntryVitalityImage> vitalityImages = entry.getVitalityImages();
							for (MdiEntryVitalityImage vi : vitalityImages) {
								if (vi.getImageID().contains("turnon")) {
									vi.setVisible(false);
								}
							}

							List<Runnable> to_fire;

							synchronized( to_fire_on_complete ){

								to_fire = new ArrayList<>( to_fire_on_complete );

								to_fire_on_complete.clear();
							}

							for ( Runnable r: to_fire ){

								if ( r != null ){

									try{
										Utils.execSWTThread( r );

									}catch( Throwable e ){

										Debug.out( e );
									}
								}
							}
						}

						@Override
						public void
						cancelled(){
							close();
						}

						@Override
						public void failed(PluginException e) {

							Debug.out(e);
							//Utils.openMessageBox(Utils.findAnyShell(), SWT.OK, "Error",
							//		e.toString());
							close();
						}
					});

		} catch (Throwable e) {

			Debug.printStackTrace(e);
		}
	}

	/**
	 *
	 *
	 * @since 4.1.0.5
	 */
	protected void close() {
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				if (shell != null && !shell.isDisposed()) {
					shell.dispose();
				}
			}
		});
	}

	/**
	 * @since 4.1.0.5
	 */
	public static boolean ensureInstalled( final Runnable fire_on_install ) {
		DeviceManager device_manager = DeviceManagerFactory.getSingleton();
		if (device_manager == null) {
			return false;
		}

		if (device_manager.getTranscodeManager().getProviders().length == 0 && !DeviceManagerUI.DISABLED_TRANSCODING) {
			Utils.execSWTThread(new AERunnable() {
				@Override
				public void runSupport() {
					if (instance == null || instance.isDisposed()) {
						instance = new DevicesFTUX();
						instance.open( fire_on_install );
					} else {
						instance.setFocus( fire_on_install );
					}
				}
			});
			return false;
		}
		return true;
	}

	public static void showForDebug() {
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				if (instance == null || instance.isDisposed()) {
					instance = new DevicesFTUX();
					instance.open( null );
				} else {
					instance.setFocus( null );
				}
			}
		});
	}

}
