/*
 * Created on 27 Jul 2006
 * Created by Paul Gardner
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 */

package com.biglybt.ui.swt.progress;

import com.biglybt.core.Core;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Shell;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.AEThread2;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.DelayedEvent;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.mainwindow.Colors;
import com.biglybt.core.CoreOperation;
import com.biglybt.core.CoreOperationListener;
import com.biglybt.core.CoreOperationTask;
import com.biglybt.ui.swt.imageloader.ImageLoader;

public class
ProgressWindow
{
	public static void
	register(
		Core core )
	{
		core.addOperationListener(
			new CoreOperationListener()
			{
				@Override
				public boolean
				operationCreated(
					CoreOperation operation )
				{
					if ( 	( 	operation.getOperationType() == CoreOperation.OP_FILE_MOVE ||
								operation.getOperationType() == CoreOperation.OP_PROGRESS )&&
							Utils.isThisThreadSWT()){

						if ( operation.getTask() != null ){

							new ProgressWindow( operation );

							return( true );
						}
					}

					return( false );
				}
			});
	}

	private volatile Shell 			shell;
	private volatile boolean 		task_complete;

	private final 	String	 resource;
	private Image[] spinImages;
	private int curSpinIndex = 0;

	private ProgressBar progress_bar;

	
	protected
	ProgressWindow(
		final CoreOperation operation )
	{
		final RuntimeException[] error = {null};

		resource = operation.getOperationType()== CoreOperation.OP_FILE_MOVE?"progress.window.msg.filemove":"progress.window.msg.progress";

		new DelayedEvent(
				"ProgWin",
				operation.getOperationType()== CoreOperation.OP_FILE_MOVE?1000:10,
				new AERunnable()
				{
					@Override
					public void
					runSupport()
					{
						if ( !task_complete ){

							Utils.execSWTThread(
								new Runnable()
								{
									@Override
									public void
									run()
									{
										synchronized( ProgressWindow.this ){

											if ( !task_complete ){

												Shell shell = com.biglybt.ui.swt.components.shell.ShellFactory.createMainShell(
														( SWT.DIALOG_TRIM ));	// parg: removed modal - people complain about this locking the UI, let it be on their own heads if they go and screw with things then

												showDialog( shell, operation );
											}
										}
									}
								},
								false );
						}
					}
				});

		new AEThread2( "ProgressWindow", true )
		{
			@Override
			public void
			run()
			{
				try{
					// Thread.sleep(10000);

					CoreOperationTask task = operation.getTask();

					if ( task == null ){

						throw( new RuntimeException( "Task not available" ));
					}

					task.run( operation );

				}catch( RuntimeException e ){

					error[0] = e;

				}catch( Throwable e ){

					error[0] = new RuntimeException( e );

				}finally{

					Utils.execSWTThread(
							new Runnable()
							{
								@Override
								public void
								run()
								{
									destroy();
								}
							});
				}
			}
		}.start();

		try{
			final Display display = Utils.getDisplay();

			while( !( task_complete || display.isDisposed())){

				if (!display.readAndDispatch()) display.sleep();
			}
		}finally{

				// bit of boiler plate in case something fails in the dispatch loop

			synchronized( ProgressWindow.this ){

				task_complete = true;
			}

			try{
				if ( shell != null && !shell.isDisposed()){

					shell.dispose();
				}
			}catch( Throwable e ){

				Debug.printStackTrace(e);
			}
		}

		if ( error[0] != null ){

			throw( error[0] );
		}
	}

	public
	ProgressWindow(
		Shell		_parent,
		String		_resource,
		int			_style,
		int			_delay_millis )
	{
		resource = _resource;

		final Shell shell = new Shell( _parent, _style );

		if ( _delay_millis <= 0 ){

			showDialog( shell, null );

		}else{

			new DelayedEvent(
					"ProgWin",
					_delay_millis,
					new AERunnable()
					{
						@Override
						public void
						runSupport()
						{
							if ( !task_complete ){

								Utils.execSWTThread(
									new Runnable()
									{
										@Override
										public void
										run()
										{
											synchronized( ProgressWindow.this ){

												if ( !task_complete ){

													showDialog( shell, null );
												}
											}
										}
									},
									false );
							}
						}
					});
		}
	}

	protected void
	showDialog(
		Shell			_shell,
		CoreOperation	_core_op )
	{
		shell	= _shell;

		shell.setText( MessageText.getString( "progress.window.title" ));

		final CoreOperationTask.ProgressCallback progress = _core_op==null?null:_core_op.getTask().getProgressCallback();
		
		Utils.setShellIcon(shell);

		/*
		shell.addListener(
				SWT.Close,
				new Listener()
				{
					public void
					handleEvent(
						org.eclipse.swt.widgets.Event event)
					{
						event.doit = false;
					}
				});
		*/

		GridLayout layout = new GridLayout();
		layout.numColumns = 2;
		shell.setLayout(layout);

		shell.setBackground( Colors.white );
		
		spinImages = ImageLoader.getInstance().getImages("working");
		
		if ( spinImages == null || spinImages.length == 0 ){

			new Label( shell, SWT.NULL );

		}else{

			final Rectangle spinBounds = spinImages[0].getBounds();
			
		    final Canvas	canvas =
		    	new Canvas( shell, SWT.DOUBLE_BUFFERED )
		    	{
		    		@Override
				    public Point computeSize(int wHint, int hHint, boolean changed )
		    		{
		    			return( new Point(spinBounds.width, spinBounds.height));
		    		}
		    	};

	    	canvas.addPaintListener(new PaintListener() {
					@Override
					public void paintControl(PaintEvent e) {
						e.gc.drawImage(spinImages[curSpinIndex ], 0, 0);
						
						if ( progress != null && progress_bar != null ){
							
							int p =  progress.getProgress();
							
							progress_bar.setSelection( p );
						}
					}
				});

	    	Utils.execSWTThreadLater(100, new AERunnable() {
					@Override
					public void runSupport() {
						if (canvas == null || canvas.isDisposed()) {
							return;
						}

						canvas.redraw();
						//canvas.update();
						if (curSpinIndex == spinImages.length - 1) {
							curSpinIndex = 0;
						} else {
							curSpinIndex++;
						}
						Utils.execSWTThreadLater(100, this);
					}
				});

	    	canvas.setBackground( Colors.white );
		}


		Label label = new Label(shell, SWT.NONE);

		label.setText(MessageText.getString( resource ));
		GridData gridData = new GridData();
		label.setLayoutData(gridData);
		label.setBackground( Colors.white );
		
		if ( progress != null ){
		
			progress_bar = new ProgressBar(shell,SWT.HORIZONTAL );
			progress_bar.setMinimum(0);
			progress_bar.setMaximum(1000);
			progress_bar.setBackground( Colors.white );
			gridData = new GridData( GridData.FILL_HORIZONTAL );
			gridData.horizontalSpan = 2;
			
			progress_bar.setLayoutData( gridData );
		}
		
		shell.pack();

		Composite parent = shell.getParent();

		if ( parent != null ){

			Utils.centerWindowRelativeTo( shell, parent );

		}else{

			Utils.centreWindow( shell );
		}

		shell.open();
	}

	public void
	destroy()
	{
		synchronized( ProgressWindow.this ){

			task_complete = true;
		}

		try{
			if ( shell != null && !shell.isDisposed()){

				shell.dispose();
			}
		}catch( Throwable e ){

			Debug.printStackTrace(e);
		}

		if (spinImages != null) {
			ImageLoader.getInstance().releaseImage("working");
			spinImages =  null;
		}
	}
}
