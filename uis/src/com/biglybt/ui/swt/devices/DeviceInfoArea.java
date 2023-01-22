/*
 * Created on Mar 2, 2009
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


import com.biglybt.ui.swt.mdi.BaseMDI;
import com.biglybt.ui.swt.mdi.MdiEntrySWT;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;
import com.biglybt.pif.installer.*;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.views.utils.ManagerUtils;

import com.biglybt.core.CoreFactory;
import com.biglybt.core.devices.*;
import com.biglybt.ui.swt.skin.SWTSkinObject;
import com.biglybt.ui.swt.views.skin.SkinView;

/**
 * @author TuxPaper
 * @created Mar 2, 2009
 *
 */
public class DeviceInfoArea
	extends SkinView
{
	private DeviceMediaRenderer device;
	private Composite main;
	private Composite parent;

	// @see SkinView#skinObjectInitialShow(SWTSkinObject, java.lang.Object)
	@Override
	public Object skinObjectInitialShow(SWTSkinObject skinObject, Object params) {

		
		MdiEntrySWT entry = BaseMDI.getEntryFromSkinObject(skinObject);;
		if (entry != null) {
			device = (DeviceMediaRenderer) entry.getDataSource();
		}

		parent = (Composite) skinObject.getControl();

		return null;
	}

	// @see SkinView#skinObjectShown(SWTSkinObject, java.lang.Object)
	@Override
	public Object skinObjectShown(SWTSkinObject skinObject, Object params) {
		super.skinObjectShown(skinObject, params);

		if (device == null) {
			initDeviceOverview();
		} else {
			initDeviceView();
		}
		return null;
	}

	// @see SkinView#skinObjectHidden(SWTSkinObject, java.lang.Object)
	@Override
	public Object skinObjectHidden(SWTSkinObject skinObject, Object params) {
		Utils.disposeComposite(main);
		return super.skinObjectHidden(skinObject, params);
	}

	/**
	 *
	 *
	 * @since 4.1.0.5
	 */
	private void initDeviceView() {
		main = new Composite( parent, SWT.NONE);
		GridLayout layout = new GridLayout();
		layout.numColumns = 1;
		layout.marginTop = 4;
		layout.marginBottom = 4;
		layout.marginHeight = 4;
		layout.marginWidth = 4;
		main.setLayout(layout);
		GridData grid_data;
		main.setLayoutData(Utils.getFilledFormData());

			// control

		Composite control = new Composite(main, SWT.NONE);
		layout = new GridLayout();
		layout.numColumns = 3;
		layout.marginLeft = 0;
		control.setLayout(layout);

			// browse to local dir

		grid_data = new GridData(GridData.FILL_HORIZONTAL);
		grid_data.horizontalSpan = 1;
		control.setLayoutData(grid_data);

			Label dir_lab = new Label( control, SWT.NONE );
			dir_lab.setText( "Local directory: " + device.getWorkingDirectory().getAbsolutePath());


			Button show_folder_button = new Button( control, SWT.PUSH );

		 	Messages.setLanguageText( show_folder_button, "MyTorrentsView.menu.explore");

		 	show_folder_button.addSelectionListener(
		 		new SelectionAdapter()
		 		{
		 			@Override
				  public void
		 			widgetSelected(
		 				SelectionEvent e )
		 			{

		 				ManagerUtils.open( device.getWorkingDirectory());
		 			}
		 		});

		 	new Label( control, SWT.NONE );

		 	if ( device.canFilterFilesView()){

				final Button show_xcode_button = new Button( control, SWT.CHECK );

			 	Messages.setLanguageText( show_xcode_button, "devices.xcode.only.show");

			 	show_xcode_button.setSelection( device.getFilterFilesView());

			 	show_xcode_button.addSelectionListener(
			 		new SelectionAdapter()
			 		{
			 			@Override
					  public void
			 			widgetSelected(
			 				SelectionEvent e )
			 			{
			 				device.setFilterFilesView( show_xcode_button.getSelection());
			 			}
			 		});
		 	}

		 	final Button btnReset = new Button(main, SWT.PUSH);
		 	btnReset.setText("Forget Default Profile Choice");
		 	btnReset.addSelectionListener(new SelectionListener() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					device.setDefaultTranscodeProfile(null);
					btnReset.setEnabled(false);
				}

				@Override
				public void widgetDefaultSelected(SelectionEvent e) {
				}
			});
			try {
				btnReset.setEnabled(device.getDefaultTranscodeProfile() != null);
			} catch (TranscodeException e1) {
				btnReset.setEnabled(false);
			}
			btnReset.setLayoutData(new GridData());

		 	parent.getParent().layout();
	}

	protected void initDeviceOverview() {
		// DeviceInfoArea isn't used, but if it were we'd want to
		// do a check to see if Core is available yet..
		final PluginInstaller installer = CoreFactory.getSingleton().getPluginManager().getPluginInstaller();
		boolean hasItunes = CoreFactory.getSingleton().getPluginManager().getPluginInterfaceByID(
				"azitunes") != null;

		main = new Composite( parent, SWT.NONE);
		main.setLayoutData(Utils.getFilledFormData());
		FormLayout layout = new FormLayout();
		layout.marginWidth = layout.marginHeight = 5;
		main.setLayout(layout);


		FormData fd;

		Control top;
		if (hasItunes) {
			Button itunes_button = new Button(main, SWT.NULL);
			top = itunes_button;

			itunes_button.setText("Install iTunes Integration");

			itunes_button.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event arg0) {
					try {
						StandardPlugin itunes_plugin = installer.getStandardPlugin("azitunes");

						if ( itunes_plugin == null ){

							Debug.out( "iTunes standard plugin not found");

						}else{

							itunes_plugin.install(false);
						}

					} catch (Throwable e) {

						Debug.printStackTrace(e);
					}
				}
			});

			fd = new FormData();
			fd.left = new FormAttachment(0, 0);
			fd.top = new FormAttachment(0, 4);

			itunes_button.setLayoutData(fd);
		} else {
			Label lblItunesInstalled = new Label(main, SWT.WRAP);
			top = lblItunesInstalled;
			lblItunesInstalled.setText("iTunes support is available");
		}

		if (Constants.isCVSVersion()) {
			//buildBetaArea(main, top);
		}
		parent.getParent().layout();
	}

/*
	private void buildBetaArea(Composite parent, Control above) {
		FormData fd;

		Group betaArea = new Group(parent, SWT.NONE);
		betaArea.setText("Beta Debug");
		betaArea.setLayout(new FormLayout());
		fd = Utils.getFilledFormData();
		fd.top = new FormAttachment(above, 5);
		betaArea.setLayoutData(fd);

		fd = new FormData();
		fd.left 	= new FormAttachment(0,0);
		fd.right	= new FormAttachment(100,0);
		fd.top	= new FormAttachment(0, 0);

		Label label = new Label( betaArea, SWT.NULL );

		label.setText( "Transcode Providers:" );

		label.setLayoutData( fd );

		Button vuze_button = new Button( betaArea, SWT.NULL );

		vuze_button.setText( "Install Vuze Transcoder" );

		if (CoreFactory.isCoreRunning()) {
			final PluginInstaller installer = CoreFactory.getSingleton().getPluginManager().getPluginInstaller();

			StandardPlugin vuze_plugin = null;

			try{
				vuze_plugin = installer.getStandardPlugin( "vuzexcode" );

			}catch( Throwable e ){
			}

			if ( vuze_plugin == null || vuze_plugin.isAlreadyInstalled()){

				vuze_button.setEnabled( false );
			}

			final StandardPlugin	f_vuze_plugin = vuze_plugin;

			vuze_button.addListener(
					SWT.Selection,
					new Listener()
					{
						@Override
						public void
						handleEvent(
								Event arg0 )
						{
							try{
								f_vuze_plugin.install( false );

							}catch( Throwable e ){

								Debug.printStackTrace(e);
							}
						}
					});

			fd = new FormData();
			fd.left 	= new FormAttachment(0,0);
			fd.top	= new FormAttachment(label,4);

			vuze_button.setLayoutData( fd );
		}


		Control top = vuze_button;


		TranscodeProvider[] providers = DeviceManagerFactory.getSingleton().getTranscodeManager().getProviders();

		for ( TranscodeProvider provider: providers ){

			fd = new FormData();
			fd.left 	= new FormAttachment(0,10);
			fd.right	= new FormAttachment(100,0);
			fd.top	= new FormAttachment(top,4);

			Label prov_lab = new Label( betaArea, SWT.NULL );

			prov_lab.setText( provider.getName());

			prov_lab.setLayoutData( fd );

			top = prov_lab;

			TranscodeProfile[] profiles = provider.getProfiles();

			String line = null;
			for ( TranscodeProfile profile: profiles ){

				if (line == null) {
					line = profile.getName();
				} else {
					line += ", " + profile.getName();
				}

			}

			if (line != null) {
  			fd = new FormData();
  			fd.left 	= new FormAttachment(0,25);
  			fd.right	= new FormAttachment(100,0);
  			fd.top	= new FormAttachment(top,4);

  			Label prof_lab = new Label( betaArea, SWT.WRAP );

  			prof_lab.setText("Profiles: " + line);

  			prof_lab.setLayoutData( fd );

  			top = prof_lab;
			}
		}

			// both - installer test

		final Button both_button = new Button( betaArea, SWT.NULL );

		both_button.setText( "Test! Install RSSGen and AZBlog!" );


		if (CoreFactory.isCoreRunning()) {
			final PluginInstaller installer = CoreFactory.getSingleton().getPluginManager().getPluginInstaller();

			StandardPlugin plugin1 = null;
			StandardPlugin plugin2 = null;

			try{
				plugin1 = installer.getStandardPlugin( "azrssgen" );

			}catch( Throwable e ){
			}

			try{
				plugin2 = installer.getStandardPlugin( "azblog" );

			}catch( Throwable e ){
			}

			if ( plugin1 != null && plugin2 != null ){

				final Composite install_area = new Composite( betaArea, SWT.BORDER );

				fd = new FormData();
				fd.left 	= new FormAttachment(both_button,0);
				fd.right	= new FormAttachment(100,0);
				fd.top	= new FormAttachment(top,4);
				fd.bottom	= new FormAttachment(100,0);

				install_area.setLayoutData( fd );

				final StandardPlugin	f_plugin1 = plugin1;
				final StandardPlugin	f_plugin2 = plugin2;

				both_button.addListener(
						SWT.Selection,
						new Listener()
						{
							@Override
							public void
							handleEvent(
									Event arg0 )
							{
								both_button.setEnabled( false );

								try{
									Map<Integer,Object>	properties = new HashMap<>();

									properties.put( UpdateCheckInstance.PT_UI_STYLE, UpdateCheckInstance.PT_UI_STYLE_SIMPLE );

									properties.put( UpdateCheckInstance.PT_UI_PARENT_SWT_COMPOSITE, install_area );

									properties.put( UpdateCheckInstance.PT_UI_DISABLE_ON_SUCCESS_SLIDEY, true );

									installer.install(
											new InstallablePlugin[]{ f_plugin1, f_plugin2 },
											false,
											properties,
											new PluginInstallationListener()
											{
												@Override
												public void
												completed()
												{
													System.out.println( "Install completed!" );

													tidy();
												}

												@Override
												public void
												cancelled()
												{
													System.out.println( "Install cancelled" );

													tidy();
												}

												@Override
												public void
												failed(
														PluginException	e )
												{
													System.out.println( "Install failed: " + e );

													tidy();
												}

												protected void
												tidy()
												{
													Utils.execSWTThread(
															new Runnable()
															{
																@Override
																public void
																run()
																{
																	Control[] kids = install_area.getChildren();

																	for ( Control c: kids ){

																		c.dispose();
																	}

																	both_button.setEnabled( true );
																}
															});
												}
											});

								}catch( Throwable e ){

									Debug.printStackTrace(e);
								}
							}
						});
			}else{

				both_button.setEnabled(false);
			}

			fd = new FormData();
			fd.left 	= new FormAttachment(0,0);
			fd.top	= new FormAttachment(top,4);
			fd.bottom	= new FormAttachment(100,0);

			both_button.setLayoutData( fd );
		}

	}
	*/
}
