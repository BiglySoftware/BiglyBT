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
import com.biglybt.core.CoreFactory;

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
import com.biglybt.core.config.COConfigurationManager;
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
				operationExecuteRequest(
					CoreOperation operation )
				{
					int type = operation.getOperationType();
					
					if ( 	( 	type == CoreOperation.OP_FILE_MOVE ||
								type == CoreOperation.OP_DOWNLOAD_EXPORT ||
								type == CoreOperation.OP_PROGRESS ) &&
							Utils.isThisThreadSWT()){

						CoreOperationTask task = operation.getTask();
						
						if ( task != null ){

							if ( 	type ==  CoreOperation.OP_FILE_MOVE && 
									COConfigurationManager.getBooleanParameter("Suppress File Move Dialog" )){

								AEThread2.createAndStartDaemon( "Core Operation", ()->{
									
									try{
										task.run( operation );
										
									}finally{
										
										core.removeOperation( operation );
									}
								});
								
							}else{
								
								new ProgressWindow( operation );
							}
							
							return( true );
						}
					}

					return( false );
				}
				
				@Override
				public void 
				operationAdded(
					CoreOperation operation )
				{
				}
				
				@Override
				public void 
				operationRemoved(
					CoreOperation operation )
				{
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
	
	private
	ProgressWindow(
		final CoreOperation operation )
	{
		final RuntimeException[] error = {null};

		int	op_type = operation.getOperationType();
		
		int delay = 1000;
		
		if ( op_type == CoreOperation.OP_FILE_MOVE ){
			resource = "progress.window.msg.filemove";
		}else if ( op_type == CoreOperation.OP_DOWNLOAD_EXPORT ){
			resource = "progress.window.msg.dlexport";
		}else{
			resource = "progress.window.msg.progress";
			
			delay = 10;
			
			ProgressCallback cb = operation.getTask().getProgressCallback();
			
			if ( cb != null ){
				
				int del = cb.getDelay();
				
				if ( del > 10 ){
					
					delay = del;
				}
			}
		}
		
		new DelayedEvent(
				"ProgWin",
				delay,
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

													// parg: removed general modal - people complain about this locking the UI, let it be on their own heads if they go and screw with things then

												int style = SWT.DIALOG_TRIM | SWT.RESIZE;
												
												if ( op_type == CoreOperation.OP_PROGRESS ){
													
													ProgressCallback cb = operation.getTask().getProgressCallback();
													
													if ( cb != null ){
														
														int s = cb.getStyle();
														
														if ((s & ProgressCallback.STYLE_NO_CLOSE) != 0 ){
															
															style &= ~SWT.CLOSE;
														}
														if ((s & ProgressCallback.STYLE_MODAL) != 0 ){
															
															style |= SWT.APPLICATION_MODAL;
														}
													}
												}
												
												Shell shell = com.biglybt.ui.swt.components.shell.ShellFactory.createMainShell(( style ));
												
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
					
						// report complete now as the SWT dispatch loop below might not exit until other windows
						// has closed their loops...
					
					CoreFactory.getSingleton().removeOperation( operation );
					
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

		if ( shell == null || shell.isDisposed()){
			
			return;
		}
		
		boolean closeable = (shell.getStyle() & SWT.CLOSE ) != 0;
		
		shell.setText( MessageText.getString( "progress.window.title" ));

		CoreOperationTask task = _core_op==null?null:_core_op.getTask();
		
		ProgressCallback progress = task==null?null:task.getProgressCallback();
		
		boolean alreadyPositioned = Utils.linkShellMetricsToConfig( shell, "com.biglybt.ui.swt.progress.ProgressWindow" + "." + _core_op.getOperationType());
		
		Utils.setShellIcon(shell);

		if ( !closeable ){
			
			shell.addListener( SWT.Close, (ev)->{ ev.doit = false; });
		}

		GridLayout layout = new GridLayout();
		layout.numColumns = 2;
		shell.setLayout(layout);

		Color bg = Utils.isDarkAppearancePartial()?null:Colors.white;
		
		shell.setBackground( bg );
		
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
				lName.setBackground( bg );
				
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
					subtask_label.setBackground( bg );
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
					}
				});

	    	Utils.execSWTThreadLater(100, new AERunnable() {
					@Override
					public void runSupport() {
						if (canvas == null || canvas.isDisposed()) {
							return;
						}

						canvas.redraw();
						
						if (curSpinIndex == spinImages.length - 1) {
							curSpinIndex = 0;
						} else {
							curSpinIndex++;
						}
						
						if ( progress != null && progress_bar != null ){
							
							if ( progress_bar.isDisposed()){
								
								return;
							}
							
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
						
						Utils.execSWTThreadLater(100, this);
					}
				});

	    	canvas.setBackground( bg );
		}


		Label label = new Label(shell, SWT.NONE);

		label.setText(MessageText.getString( resource ));
		GridData gridData = new GridData();
		label.setLayoutData(gridData);
		label.setBackground( bg );
		
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
			compProg.setBackground( bg );
			
			progress_bar = new ProgressBar(compProg,SWT.HORIZONTAL );
			progress_bar.setMinimum(0);
			progress_bar.setMaximum(1000);
			progress_bar.setBackground( bg );
			gridData = new GridData( GridData.FILL_HORIZONTAL );
			gridData.widthHint = 400;
			progress_bar.setLayoutData(gridData);

			int states = progress.getSupportedTaskStates();
			
			if ((states & ProgressCallback.ST_BUTTONS) != 0 ){
				
				Label labelSeparator = new Label(shell,SWT.SEPARATOR | SWT.HORIZONTAL);
				gridData = new GridData(GridData.FILL_HORIZONTAL);
				gridData.horizontalSpan = 2;
				labelSeparator.setLayoutData(gridData);

				// buttons
		
				boolean 	has_pause_resume 	= ( states & ( ProgressCallback.ST_PAUSE | ProgressCallback.ST_RESUME  )) != 0;
				boolean 	has_cancel			= ( states & ProgressCallback.ST_CANCEL ) != 0;
				
					// must have something...
				
				if ( !has_pause_resume ){
					has_cancel = true;
				}
				
				int	 num_buttons = 0;
				
				if ( has_pause_resume  )num_buttons+=2;
				if ( has_cancel  )num_buttons++;
				
				Composite comp = new Composite(shell,SWT.NULL);
				gridData = new GridData(GridData.FILL_HORIZONTAL | GridData.HORIZONTAL_ALIGN_END | GridData.HORIZONTAL_ALIGN_FILL);
				gridData.grabExcessHorizontalSpace = true;
				gridData.horizontalSpan = 2;
				comp.setLayoutData(gridData);
				GridLayout layoutButtons = new GridLayout();
				layoutButtons.numColumns = num_buttons;
				comp.setLayout(layoutButtons);
				comp.setBackground( bg );
		
				List<Button> buttons = new ArrayList<>();
		
				Button bPause;
				Button bResume;
				Button bCancel;
				
				if ( has_pause_resume ){
					bPause = new Button(comp,SWT.PUSH);
					bPause.setText(MessageText.getString("v3.MainWindow.button.pause"));
					gridData = new GridData(GridData.FILL_HORIZONTAL | GridData.HORIZONTAL_ALIGN_END | GridData.HORIZONTAL_ALIGN_FILL);
					gridData.grabExcessHorizontalSpace = true;
					//gridData.widthHint = 70;
					bPause.setLayoutData(gridData);
					buttons.add( bPause );
					
					bResume = new Button(comp,SWT.PUSH);
					bResume.setText(MessageText.getString("v3.MainWindow.button.resume"));
					gridData = new GridData(GridData.FILL_HORIZONTAL | GridData.HORIZONTAL_ALIGN_END | GridData.HORIZONTAL_ALIGN_FILL);
					gridData.grabExcessHorizontalSpace = false;
					//gridData.widthHint = 70;
					bResume.setLayoutData(gridData);
					buttons.add( bResume );
				}else{
					bPause	= null;
					bResume = null;
				}
				
				if ( has_cancel ){
					bCancel = new Button(comp,SWT.PUSH);
					bCancel.setText(MessageText.getString("UpdateWindow.cancel"));
					gridData = new GridData(GridData.HORIZONTAL_ALIGN_END);
					gridData.grabExcessHorizontalSpace = false;
					//gridData.widthHint = 70;
					bCancel.setLayoutData(gridData);
					buttons.add( bCancel );
				}else{
					bCancel = null;
				}
				
				Utils.makeButtonsEqualWidth( buttons );
				
				if ( has_pause_resume ){
					
						// if we have resume then we have pause
					
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
				}
				
				if ( bCancel != null ){
					
					bCancel.addListener(SWT.Selection,new Listener() {
						@Override
						public void handleEvent(Event e) {
							if ( has_pause_resume ){
								bPause.setEnabled( false );
								bResume.setEnabled( false );
							}
							bCancel.setEnabled( false );
							progress.setTaskState( ProgressCallback.ST_CANCEL );
						}
					});
				}
				
				Utils.execSWTThreadLater(
					250,
					new Runnable()
					{
						@Override
						public void run(){
							if ( comp.isDisposed()){
								return;
							}
							
							int state = progress.getTaskState();
							
							if ( state == ProgressCallback.ST_CANCEL ){
								
								shell.dispose();
								
							}else{
								
								if ( has_pause_resume ){
									
									if ( state == ProgressCallback.ST_PAUSE ){
										
										bPause.setEnabled( false );
										
										bResume.setEnabled( true );
										
									}else{
										
										bPause.setEnabled( true );
										
										bResume.setEnabled( false );
									}
								}
								
								Utils.execSWTThreadLater( 250, this );
							}
						}
					});
				
				shell.setDefaultButton( has_pause_resume?bPause:bCancel );
				
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
