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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
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
import com.biglybt.core.CoreOperationTask.ProgressCallback;
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
								operation.getOperationType() == CoreOperation.OP_DOWNLOAD_EXPORT ||
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
	private Label		subtask_label;
	
	private boolean	task_paused;
	
	protected
	ProgressWindow(
		final CoreOperation operation )
	{
		final RuntimeException[] error = {null};

		int	op_type = operation.getOperationType();
		
		if ( op_type == CoreOperation.OP_FILE_MOVE ){
			resource = "progress.window.msg.filemove";
		}else if ( op_type == CoreOperation.OP_DOWNLOAD_EXPORT ){
			resource = "progress.window.msg.dlexport";
		}else{
			resource = "progress.window.msg.progress";
		}
		
		new DelayedEvent(
				"ProgWin",
				operation.getOperationType()== CoreOperation.OP_PROGRESS?10:1000,
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
														( SWT.DIALOG_TRIM | SWT.RESIZE ));	// parg: removed modal - people complain about this locking the UI, let it be on their own heads if they go and screw with things then

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

		CoreOperationTask task = _core_op==null?null:_core_op.getTask();
		
		CoreOperationTask.ProgressCallback progress = task==null?null:task.getProgressCallback();
		
		boolean alreadyPositioned = Utils.linkShellMetricsToConfig( shell, "com.biglybt.ui.swt.progress.ProgressWindow" + "." + _core_op.getOperationType());
		
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
		
		if ( task != null ){
			
			String name = task.getName();
			
			if ( name != null ){
				
				Label lName = new Label(shell, SWT.NONE);

				FontData fontData = lName.getFont().getFontData()[0];
				
				Font bold_font 	= new Font( shell.getDisplay(), new FontData( fontData.getName(), fontData.getHeight(), SWT.BOLD ));
				
				lName.setText( name );
				GridData gridData = new GridData( GridData.FILL_HORIZONTAL );
				gridData.horizontalSpan = 2;
				lName.setLayoutData(gridData);
				lName.setBackground( Colors.white );
				
				lName.setFont( bold_font );
				
				lName.addDisposeListener(
					new DisposeListener(){
						
						@Override
						public void widgetDisposed(DisposeEvent arg0){
							bold_font.dispose();
						}
					});
				
				
				if ( progress != null && (  progress.getSupportedTaskStates() & ProgressCallback.ST_SUBTASKS ) != 0 ){
					
					subtask_label = new Label(shell, SWT.NONE);
					
					gridData = new GridData( GridData.FILL_HORIZONTAL );
					gridData.horizontalSpan = 2;
					gridData.horizontalIndent = 25;
					subtask_label.setLayoutData(gridData);
					subtask_label.setBackground( Colors.white );
				}
			}
		}
		
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
							
							if ( subtask_label != null ){
							
								String st = progress.getSubTaskName();
								
								if ( st == null ){
									
									st = "";
								}
								
								subtask_label.setText( st );
							}
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
		
			Composite compProg = new Composite(shell,SWT.BORDER);
			gridData = new GridData(GridData.FILL_HORIZONTAL);
			gridData.grabExcessHorizontalSpace = true;
			gridData.horizontalSpan = 2;
			compProg.setLayoutData(gridData);
			GridLayout layoutProgress = new GridLayout();
			layoutProgress.numColumns = 1;
			layoutProgress.marginWidth = layoutProgress.marginHeight = 0;
			compProg.setLayout(layoutProgress);
			compProg.setBackground( Colors.white );
			
			progress_bar = new ProgressBar(compProg,SWT.HORIZONTAL );
			progress_bar.setMinimum(0);
			progress_bar.setMaximum(1000);
			progress_bar.setBackground( Colors.white );
			gridData = new GridData( GridData.FILL_HORIZONTAL );
			gridData.widthHint = 400;
			progress_bar.setLayoutData(gridData);

			int states = progress.getSupportedTaskStates();
			
			if ( states != ProgressCallback.ST_NONE ){
				
				Label labelSeparator = new Label(shell,SWT.SEPARATOR | SWT.HORIZONTAL);
				gridData = new GridData(GridData.FILL_HORIZONTAL);
				gridData.horizontalSpan = 2;
				labelSeparator.setLayoutData(gridData);

				// buttons
		
				Composite comp = new Composite(shell,SWT.NULL);
				gridData = new GridData(GridData.FILL_HORIZONTAL | GridData.HORIZONTAL_ALIGN_END | GridData.HORIZONTAL_ALIGN_FILL);
				gridData.grabExcessHorizontalSpace = true;
				gridData.horizontalSpan = 2;
				comp.setLayoutData(gridData);
				GridLayout layoutButtons = new GridLayout();
				layoutButtons.numColumns = 3;
				comp.setLayout(layoutButtons);
				comp.setBackground( Colors.white );
		
				List<Button> buttons = new ArrayList<>();
		
				Button bPause = new Button(comp,SWT.PUSH);
				bPause.setText(MessageText.getString("v3.MainWindow.button.pause"));
				gridData = new GridData(GridData.FILL_HORIZONTAL | GridData.HORIZONTAL_ALIGN_END | GridData.HORIZONTAL_ALIGN_FILL);
				gridData.grabExcessHorizontalSpace = true;
				//gridData.widthHint = 70;
				bPause.setLayoutData(gridData);
				buttons.add( bPause );
		
				Button bResume = new Button(comp,SWT.PUSH);
				bResume.setText(MessageText.getString("v3.MainWindow.button.resume"));
				gridData = new GridData(GridData.FILL_HORIZONTAL | GridData.HORIZONTAL_ALIGN_END | GridData.HORIZONTAL_ALIGN_FILL);
				gridData.grabExcessHorizontalSpace = false;
				//gridData.widthHint = 70;
				bResume.setLayoutData(gridData);
				buttons.add( bResume );

				
				Button bCancel = new Button(comp,SWT.PUSH);
				bCancel.setText(MessageText.getString("UpdateWindow.cancel"));
				gridData = new GridData(GridData.HORIZONTAL_ALIGN_END);
				gridData.grabExcessHorizontalSpace = false;
				//gridData.widthHint = 70;
				bCancel.setLayoutData(gridData);
				buttons.add( bCancel );

				Utils.makeButtonsEqualWidth( buttons );
				
				bResume.setEnabled( false );
				
				bPause.addListener(SWT.Selection,new Listener() {
					@Override
					public void handleEvent(Event e) {
						task_paused	= true;
						bPause.setEnabled( false );
						bResume.setEnabled( true );
						shell.setDefaultButton( bResume );
						progress.setTaskState( ProgressCallback.ST_PAUSE );
					}
				});
				
				bResume.addListener(SWT.Selection,new Listener() {
					@Override
					public void handleEvent(Event e) {
						task_paused	= false;
						bPause.setEnabled( true );
						bResume.setEnabled( false );
						shell.setDefaultButton( bPause );
						progress.setTaskState( ProgressCallback.ST_RESUME );
					}
				});
		
				bCancel.addListener(SWT.Selection,new Listener() {
					@Override
					public void handleEvent(Event e) {
						bPause.setEnabled( false );
						bResume.setEnabled( false );
						bCancel.setEnabled( false );
						progress.setTaskState( ProgressCallback.ST_CANCEL );
					}
				});
				
				shell.setDefaultButton( bPause );
				
				shell.addDisposeListener(
					new DisposeListener(){
						
						@Override
						public void widgetDisposed(DisposeEvent arg0){
							if ( task_paused ){
								progress.setTaskState( ProgressCallback.ST_RESUME );
							}
						}
					});
			}
		}
	
		
		shell.pack();

		if ( !alreadyPositioned ){
			
			Composite parent = shell.getParent();
	
			if ( parent != null ){
	
				Utils.centerWindowRelativeTo( shell, parent );
	
			}else{
	
				Utils.centreWindow( shell );
			}
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
