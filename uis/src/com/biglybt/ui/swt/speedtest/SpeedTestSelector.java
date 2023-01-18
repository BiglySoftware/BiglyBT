/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.biglybt.ui.swt.speedtest;



import java.util.HashMap;

import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import org.eclipse.swt.SWT;

import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.Debug;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.ipc.IPCException;
import com.biglybt.pif.ipc.IPCInterface;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.shells.CoreWaiterSWT;
import com.biglybt.ui.swt.wizard.AbstractWizardPanel;
import com.biglybt.ui.swt.wizard.IWizardPanel;

import com.biglybt.core.CoreRunningListener;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctionsManager;

public class
SpeedTestSelector
	extends AbstractWizardPanel<SpeedTestWizard>
{
	private boolean	mlab_test = true;

	public SpeedTestSelector(SpeedTestWizard wizard, IWizardPanel previous) {
		super(wizard, previous);
	}

	@Override
	public void show() {
		wizard.setTitle(MessageText.getString("speedtest.wizard.select.title"));
		wizard.setCurrentInfo( "" );
		final Composite rootPanel = wizard.getPanel();
		GridLayout layout = new GridLayout();
		layout.numColumns = 1;
		rootPanel.setLayout(layout);

		Composite panel = new Composite(rootPanel, SWT.NULL);
		GridData gridData = new GridData(GridData.FILL_BOTH);
		panel.setLayoutData(gridData);
		layout = new GridLayout();
		layout.numColumns = 1;
		panel.setLayout(layout);

		final Group gRadio = Utils.createSkinnedGroup(panel, SWT.NULL);
		Messages.setLanguageText(gRadio, "speedtest.wizard.select.group");
		gRadio.setLayoutData(gridData);
		layout = new GridLayout();
		layout.numColumns = 1;
		gRadio.setLayout( layout );
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		gRadio.setLayoutData(gridData);


		// general test

		Button auto_button = new Button (gRadio, SWT.RADIO);
		Messages.setLanguageText(auto_button, "speedtest.wizard.select.general");
		auto_button.setSelection( true );

		// BT

		final Button manual_button = new Button( gRadio, SWT.RADIO );
		Messages.setLanguageText(manual_button, "speedtest.wizard.select.bt");

		manual_button.setEnabled( false ); 	// currently not supported

		manual_button.addListener(
				SWT.Selection,
				new Listener()
				{
					@Override
					public void
					handleEvent(
							Event arg0 )
					{
						mlab_test = !manual_button.getSelection();
					}
				});
	}



	@Override
	public boolean
	isNextEnabled()
	{
		return( true );
	}

	@Override
	public boolean
	isPreviousEnabled()
	{
		return( false );
	}

	@Override
	public IWizardPanel
	getNextPanel()
	{
		if ( mlab_test ){

			wizard.close();

			runMLABTest( null);

			//new ConfigureWizard( false, ConfigureWizard.WIZARD_MODE_SPEED_TEST_AUTO );

			return( null );

		}else{

			return( new SpeedTestPanel( wizard, null ));
		}
	}

	public static void runMLABTest(final Runnable runWhenClosed) {
		CoreWaiterSWT.waitForCoreRunning(new CoreRunningListener() {
			@Override
			public void coreRunning(Core core) {
				UIFunctionsManager.getUIFunctions().installPlugin("mlab",
						"dlg.install.mlab", new UIFunctions.actionListener() {
							@Override
							public void actionComplete(Object result) {
								if (result instanceof Boolean) {
									_runMLABTest(runWhenClosed);
								} else {

									try {
										Throwable error = (Throwable) result;

										Debug.out(error);

									} finally {
										if (runWhenClosed != null) {
											runWhenClosed.run();
										}
									}
								}
							}
						});
			}
		});
	}

	private static void _runMLABTest(final Runnable runWhenClosed) {
		PluginInterface pi = CoreFactory.getSingleton().getPluginManager().getPluginInterfaceByID(
				"mlab");

		if ( pi == null ){
			Debug.out("mlab plugin not available");
			if (runWhenClosed != null) {
				runWhenClosed.run();
			}
		}else{
			try {
				HashMap<String, Object> map = new HashMap<>();

				pi.getIPC().invoke("runTest", new Object[] {
					map,
					new IPCInterface() {
						@Override
						public Object invoke(String methodName, Object[] params)
								throws IPCException {
							// we could set SpeedTest Completed when methodName == "results"
							// or ask user if they want to be prompted again if it isn't
							// But, we'd have to pass a param into runMLABTest (so we don't
							// get prompt on menu invocation).

							// For now, show only once, with no reprompt (even if they cancel).
							// They can use the menu
							COConfigurationManager.setParameter("SpeedTest Completed", true);

							if (runWhenClosed != null) {
								runWhenClosed.run();
							}
							return null;
						}

						@Override
						public boolean canInvoke(String methodName, Object[] params) {
							return true;
						}
					},
					true
				});

			} catch (Throwable e) {

				Debug.out(e);
				if (runWhenClosed != null) {
					runWhenClosed.run();
				}
			}
		}
	}
}
