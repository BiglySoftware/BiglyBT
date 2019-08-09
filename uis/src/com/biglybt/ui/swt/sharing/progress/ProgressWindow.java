/*
 * File    : ProgressWindow.java
 * Created : 15-Jan-2004
 * By      : parg
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

package com.biglybt.ui.swt.sharing.progress;

/**
 * @author parg
 *
 */
import com.biglybt.ui.swt.mainwindow.Colors;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.*;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.Debug;
import com.biglybt.pifimpl.local.PluginInitializer;
import com.biglybt.ui.swt.Utils;
import com.biglybt.pif.sharing.ShareException;
import com.biglybt.pif.sharing.ShareManager;
import com.biglybt.pif.sharing.ShareManagerListener;
import com.biglybt.pif.sharing.ShareResource;

public class
ProgressWindow
	implements ShareManagerListener
{
	private final ParameterListener paramSupressSharingDialogListener;
	private boolean window_disabled;

	private ShareManager	share_manager;
	private progressDialog	dialog = null;

	private Display			display;

	private StyledText		tasks;
	private ProgressBar		progress;
	private Button 			cancel_button;


	private boolean			shell_opened;
	private boolean			manually_hidden;

	public
	ProgressWindow(
		Display		_display )
	{
		paramSupressSharingDialogListener = new ParameterListener() {

			@Override
			public void parameterChanged(String parameterName) {
				window_disabled = COConfigurationManager.getBooleanParameter("Suppress Sharing Dialog");
			}
		};
		COConfigurationManager.addWeakParameterListener(
				paramSupressSharingDialogListener, true, "Suppress Sharing Dialog");

		try{
			share_manager	= PluginInitializer.getDefaultInterface().getShareManager();

			display = _display;

			share_manager.addListener(this);

		}catch( ShareException e ){

			Debug.printStackTrace( e );
		}

	}

	private class
	progressDialog
	{
		protected Shell shell;

		protected
		progressDialog(
			Display				dialog_display )
		{
			if ( dialog_display.isDisposed()){
				return;
			}

			shell = new Shell(display,SWT.ON_TOP);

			shell.setSize(250,150);
			Utils.setShellIcon(shell);

			FormLayout layout = new FormLayout();
			layout.marginHeight = 0;
			layout.marginWidth= 0;
			try {
				layout.spacing = 0;
			} catch (NoSuchFieldError e) {
				/* Ignore for Pre 3.0 SWT.. */
			} catch (Throwable e) {
				Debug.printStackTrace( e );
			}

			shell.setLayout(layout);

			shell.setText(MessageText.getString("sharing.progress.title"));


			tasks = new StyledText(shell, SWT.READ_ONLY | SWT.V_SCROLL | SWT.H_SCROLL | SWT.BORDER);
			tasks.setBackground(Colors.getSystemColor(dialog_display, SWT.COLOR_WHITE));

			progress = new ProgressBar(shell, SWT.NULL);
			progress.setMinimum(0);
			progress.setMaximum(100);


			Button hide_button = new Button(shell,SWT.PUSH);
			hide_button.setText(MessageText.getString("sharing.progress.hide"));

			cancel_button = new Button(shell,SWT.PUSH);
			cancel_button.setText(MessageText.getString("sharing.progress.cancel"));
			cancel_button.setEnabled( false );

	      //Layout :

	      //Progress Bar on bottom, with Hide button next to it.

	      FormData formData;
	      formData = new FormData();
	      formData.right = new FormAttachment(100,-5);
	      formData.bottom = new FormAttachment(100,-10);

	      hide_button.setLayoutData(formData);

	      formData = new FormData();
	      formData.right = new FormAttachment(hide_button,-5);
	      formData.bottom = new FormAttachment(100,-10);

	      cancel_button.setLayoutData(formData);

	      formData = new FormData();
	      formData.right = new FormAttachment(cancel_button,-5);
	      formData.left = new FormAttachment(0,50);
	      formData.bottom = new FormAttachment(100,-10);

	      progress.setLayoutData(formData);

	      formData = new FormData();
	      formData.right = new FormAttachment(100,-5);
	      formData.bottom = new FormAttachment(100,-50);
	      formData.top = new FormAttachment(0,5);
	      formData.left = new FormAttachment(0,5);

	      tasks.setLayoutData(formData);


	      shell.layout();

			cancel_button.addListener(SWT.Selection,new Listener() {
				@Override
				public void handleEvent(Event e) {
					cancel_button.setEnabled( false );

					share_manager.cancelOperation();
				}
			});

			hide_button.addListener(SWT.Selection,new Listener() {
				@Override
				public void handleEvent(Event e) {
					hidePanel();
				}
			});


			shell.setDefaultButton( hide_button );

			shell.addListener(SWT.Traverse, new Listener() {
				@Override
				public void handleEvent(Event e) {
					if ( e.character == SWT.ESC){
						hidePanel();
					}
				}
			});


	      Rectangle bounds = shell.getMonitor().getClientArea();
	      x0 = bounds.x + bounds.width - 255;

	      y1 = bounds.y + bounds.height - 155;

    	  shell.setLocation(x0,y1);
		}

		protected void
		hidePanel()
		{
			manually_hidden	= true;
			shell.setVisible( false );
		}

		protected void
		showPanel()
		{
			manually_hidden	= false;

			if ( !shell_opened ){

				shell_opened = true;

				shell.open();
			}




			if ( !shell.isVisible()){
				shell.setVisible(true);
			}

			shell.moveAbove(null);

		}

	protected boolean
	isShown()
	{
		return( shell.isVisible());
	}



    //Animation properties
    int x0,y1;

	}

	@Override
	public void
	resourceAdded(
		ShareResource		resource )
	{
			// we don't want to pick these additions up

		if ( !share_manager.isInitialising()){

			reportCurrentTask( "Resource added: " + resource.getName());
		}
	}

	@Override
	public void
	resourceModified(
		ShareResource		old_resource,
		ShareResource		new_resource )
	{
		reportCurrentTask( "Resource modified: " + old_resource.getName());
	}

	@Override
	public void
	resourceDeleted(
		ShareResource		resource )
	{
		reportCurrentTask( "Resource deleted: " + resource.getName());
	}

	@Override
	public void
	reportProgress(
		final int		percent_complete )
	{
		if ( window_disabled ){

			return;
		}

		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				if (progress != null && !progress.isDisposed()) {

					if (dialog == null) {
						dialog = new progressDialog(display);
						if (dialog == null) {
							return;
						}
					}

					// only allow percentage updates to make the window visible
					// if it hasn't been manually hidden

					if (!dialog.isShown() && !manually_hidden) {

						dialog.showPanel();
					}

					cancel_button.setEnabled(percent_complete < 100);

					progress.setSelection(percent_complete);
				}

			}
		});
	}

	@Override
	public void
	reportCurrentTask(
		final String	task_description )
	{
		if ( window_disabled ){

			return;
		}

		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {

				if (dialog == null) {
					dialog = new progressDialog(display);
					if (dialog == null) {
						return;
					}
				}

				if (tasks != null && !tasks.isDisposed()) {
					dialog.showPanel();

					tasks.append(task_description + Text.DELIMITER);

					int lines = tasks.getLineCount();

					// tasks(nbLines - 2, 1, colors[_color]);

					tasks.setTopIndex(lines - 1);
				}
			}
		});
	}
}
