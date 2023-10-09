/*
 * Created on 16 July 2006
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
package com.biglybt.ui.swt;

import java.util.ArrayList;
import java.util.Locale;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.FocusAdapter;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.TraverseEvent;
import org.eclipse.swt.events.TraverseListener;
import org.eclipse.swt.events.VerifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.SimpleTimer;
import com.biglybt.core.util.SystemTime;
import com.biglybt.core.util.TimerEvent;
import com.biglybt.core.util.UrlUtils;
import com.biglybt.ui.swt.components.LinkLabel;
import com.biglybt.ui.swt.pifimpl.AbstractUISWTInputReceiver;
import com.biglybt.ui.swt.utils.DragDropUtils;

import com.biglybt.pif.ui.UIInputValidator;

/**
 * @author amc1
 * Based on CategoryAdderWindow.
 */
public class SimpleTextEntryWindow extends AbstractUISWTInputReceiver {

	private Display display;
	private Shell	parent_shell;

	private Shell shell;
	private int textLimit;
	private boolean resizeable;
	private String loc_size_config_key;
	private Combo text_entry_combo;
	private Text text_entry_text;
	private Label link_label;
	private boolean detect_urls;
	private boolean special_escape_handling;
	private boolean user_hit_escape;
	
	private java.util.List<VerifyListener> verify_listeners = new ArrayList<>();
	
	public SimpleTextEntryWindow() {
	}

	public SimpleTextEntryWindow(String sTitleKey, String sLabelKey) {
		setTitle(sTitleKey);
		setMessage(sLabelKey);
	}

	public SimpleTextEntryWindow(String sTitleKey, String sLabelKey, boolean bMultiLine) {
		setTitle(sTitleKey);
		setMessage(sLabelKey);
		setMultiLine(bMultiLine);
	}

	public void initTexts(String sTitleKey, String[] p0, String sLabelKey,
			String[] p1) {
		setLocalisedTitle(MessageText.getString(sTitleKey, p0));
		setLocalisedMessage(MessageText.getString(sLabelKey, p1));
	}

	public void
	addVerifyListener(
		VerifyListener		l )
	{
		verify_listeners.add( l );
	}
	
	@Override
	protected void promptForInput() {
		Utils.execSWTThread(new Runnable() {
			@Override
			public void run() {
				promptForInput0();
				if (receiver_listener == null) {
					Utils.readAndDispatchLoop( shell );
				}
			}
		}, receiver_listener != null);
	}

	private void promptForInput0() {
		//shell = com.biglybt.ui.swt.components.shell.ShellFactory.createShell(Utils.findAnyShell(), SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
		// link to active shell, so that when it closes, the input box closes (good for config windows)

		Shell parent = parent_shell;

		if ( parent_shell == null ){

			parent = Display.getDefault().getActiveShell();
		}

		if (parent == null) {
			parent = Utils.findAnyShell();
		}
		shell = com.biglybt.ui.swt.components.shell.ShellFactory.createShell(parent, SWT.DIALOG_TRIM | (resizeable?SWT.RESIZE:0 ));

		display = shell.getDisplay();
		if (this.title != null) {
			shell.setText(this.title);
		}

		Utils.setShellIcon(shell);

	    GridLayout layout = new GridLayout();
	    layout.verticalSpacing = 10;
	    shell.setLayout(layout);

	    // Default width hint is 330.
	    
	    int width_hint;
	    
	    if ( loc_size_config_key != null && Utils.hasShellMetricsConfig( loc_size_config_key )){
	    	
	    	width_hint = -1;
	    	
	    }else{
	    
	    	width_hint = (this.width_hint == -1) ? 330 : this.width_hint;
	    }
	    
	    // Process any messages.
	   
	    GridData gridData = null;
	    for (int i=0; i<this.messages.length; i++) {
	    	String msg = messages[i];
	    	
	    	gridData = resizeable?new GridData(GridData.FILL_HORIZONTAL):new GridData();
	    	gridData.widthHint = width_hint;

	    	if ( msg.toLowerCase(Locale.US).contains( "<a href" )){
	    		
	    		StyledText text = new StyledText( shell, SWT.NULL );
	    		text.setEditable( false );
	    		text.setBackground( shell.getBackground());
	    		Utils.setTextWithURLs(text, msg,false);
	    		text.setLayoutData(gridData);
	    	}else{
	    	
		    	Label label = new Label(shell, SWT.WRAP);
		    	label.setText( msg );
		    	label.setLayoutData(gridData);
	    	}	
	    }

	    // Create Text object with pre-entered text.
	    final Scrollable text_entry;
	    if (this.choices != null) {
	    	int text_entry_flags = SWT.DROP_DOWN;
	    	if (!this.choices_allow_edit) {
	    		text_entry_flags |= SWT.READ_ONLY;
	    	}

	    	text_entry_combo = new Combo(shell, text_entry_flags);
	    	text_entry_combo.setItems(this.choices);
	    	if (textLimit > 0) {
	    		text_entry_combo.setTextLimit(textLimit);
	    	}
	    	text_entry_text = null;
	    	text_entry = text_entry_combo;
	    }
	    else {
		    // We may, at a later date, allow more customisable behaviour w.r.t. to this.
		    // (e.g. "Should we wrap this, should we provide H_SCROLL capabilities" etc.)
		    int text_entry_flags = SWT.BORDER;
		    if (this.multiline_mode) {
		    	text_entry_flags |= SWT.MULTI | SWT.V_SCROLL | SWT.WRAP;
		    }
		    else {
		    	text_entry_flags |= SWT.SINGLE;
		    }

	    	text_entry_text = new Text(shell, text_entry_flags);

	    	if (textLimit > 0) {
	    		text_entry_text.setTextLimit(textLimit);
	    	}
	    	text_entry_combo = null;
	    	text_entry = text_entry_text;
	    }
	    if (this.preentered_text != null) {
	    	if (text_entry_text != null) {
		    	text_entry_text.setText(this.preentered_text);
		    	if (this.select_preentered_text) {

		    		int[] range = this.select_preentered_text_range;

		    		if ( range == null || range.length != 2 ){
		    			text_entry_text.selectAll();
		    		}else{
		    			try{
		    				text_entry_text.setSelection( range[0], range[1] );
		    			}catch( Throwable e ){
		    				text_entry_text.selectAll();
		    			}
		    		}
		    	}
	    	}
	    	else if (text_entry_combo != null ){
		    	text_entry_combo.setText(this.preentered_text);
	    	}
	    }
	    
	    // TAB will take them out of the text entry box.
	    text_entry.addTraverseListener(new TraverseListener() {
	    	@Override
		    public void keyTraversed(TraverseEvent e) {
	    		if (e.detail == SWT.TRAVERSE_TAB_NEXT || e.detail == SWT.TRAVERSE_TAB_PREVIOUS) {
	    			e.doit = true;
	    		}
	    	}
	    });

	    text_entry.addKeyListener(new KeyListener() {

				@Override
				public void keyPressed(KeyEvent e) {
					int key = e.character;
					if (key <= 26 && key > 0) {
						key += 'a' - 1;
					}

					if (key == 'a' && e.stateMask == SWT.MOD1) {
						if (text_entry_text != null) {
							text_entry_text.selectAll();
						}
					}
				}

				@Override
				public void keyReleased(KeyEvent e) {
				}

	    });

	    for ( VerifyListener l: verify_listeners ){
			if ( text_entry_text != null ){
				
				text_entry_text.addVerifyListener( l );
				
			}else if ( text_entry_combo != null ){

				text_entry_combo.addVerifyListener( l );
			}
	    }
	    
	    // Default behaviour - single mode results in default height of 1 line,
	    // multiple lines has default height of 3.
	    int line_height = this.line_height;
	    if (line_height == -1) {
	    	line_height = (this.multiline_mode) ? 3 : 1;
	    }

	    gridData = resizeable?new GridData(GridData.FILL_BOTH):new GridData();
	    gridData.widthHint = width_hint;
	    if (text_entry_text != null){
	    	gridData.minimumHeight = text_entry_text.getLineHeight() * line_height;
	    	gridData.heightHint = gridData.minimumHeight;
	    }

		text_entry.setLayoutData(gridData);

		Composite bottom_panel = new Composite(shell, SWT.NULL);
		gridData = new GridData( GridData.FILL_HORIZONTAL );
		bottom_panel.setLayoutData(gridData); 
		GridLayout gLayout = new GridLayout( 2, false );
		gLayout.marginTop = 0;
		gLayout.marginLeft = 0;
		gLayout.marginBottom = 0;
		gLayout.marginRight = 0;
		gLayout.marginHeight = 0;
		gLayout.marginWidth = 0;
		bottom_panel.setLayout( gLayout );
		
		link_label = new Label( bottom_panel, SWT.NULL );
		gridData = new GridData( GridData.FILL_HORIZONTAL );
		link_label.setLayoutData(gridData); 
			
	    Composite button_panel = new Composite(bottom_panel, SWT.NULL);
	    RowLayout rLayout = new RowLayout();
	    rLayout.marginTop = 0;
	    rLayout.marginLeft = 0;
	    rLayout.marginBottom = 0;
	    rLayout.marginRight = 0;
    	rLayout.fill = true;
	   
	    rLayout.spacing = Utils.BUTTON_MARGIN;
	    button_panel.setLayout(rLayout);
	    gridData = new GridData();
	    gridData.horizontalAlignment = SWT.END;
	    button_panel.setLayoutData(gridData);

	    Button[] buttons = Utils.createOKCancelButtons(button_panel);

	    Button ok 		= buttons[0];
	    Button cancel 	= buttons[1];
	    
	    ok.addListener(SWT.Selection, new Listener() {

	    	private void showError(String text) {
	    		  String error_title = SimpleTextEntryWindow.this.title;
	    		  if (error_title == null) {error_title = "";}

	    		  MessageBox mb = new MessageBox(shell, SWT.ICON_ERROR | SWT.OK);
	    		  mb.setText(error_title);
	    		  mb.setMessage(text);
	    		  mb.open();
	    	}

	      /* (non-Javadoc)
	       * @see org.eclipse.swt.widgets.Listener#handleEvent(org.eclipse.swt.widgets.Event)
	       */
	      @Override
	      public void handleEvent(Event event) {
	    	  try {
	    		  String entered_data = "";
	    		  if (text_entry_text != null) {
	    			  entered_data = text_entry_text.getText();
	    		  }
	    		  else if (text_entry_combo != null) {
	    			  entered_data = text_entry_combo.getText();
	    		  }


	    		  if (!SimpleTextEntryWindow.this.maintain_whitespace) {
	    			  entered_data = entered_data.trim();
	    		  }

	    		  if (textLimit > 0 && entered_data.length() > textLimit) {
	    		  	entered_data = entered_data.substring(0, textLimit);
	    		  }

	    		  if (!SimpleTextEntryWindow.this.allow_empty_input && entered_data.length() == 0) {
	    			  showError(MessageText.getString("UI.cannot_submit_blank_text"));
	    			  return;
	    		  }

	    		  UIInputValidator validator = SimpleTextEntryWindow.this.validator;
	    		  if (validator != null) {
	    			  String validate_result = validator.validate(entered_data);
	    			  if (validate_result != null) {
		    			  showError(MessageText.getString(validate_result));
		    			  return;
	    			  }
	    		  }
	    		  SimpleTextEntryWindow.this.recordUserInput(entered_data);
	    	  }
	    	  catch (Exception e) {
	    		  Debug.printStackTrace(e);
	    		  SimpleTextEntryWindow.this.recordUserAbort();
	    	  }
	    	  shell.dispose();
	      }
	    });

	    cancel.addListener(SWT.Selection, new Listener() {
	        /* (non-Javadoc)
	         * @see org.eclipse.swt.widgets.Listener#handleEvent(org.eclipse.swt.widgets.Event)
	         */
	        @Override
	        public void handleEvent(Event event) {
	        	SimpleTextEntryWindow.this.recordUserAbort();
	            shell.dispose();
	        }
	      });

	    if ( special_escape_handling ){
		    cancel.setToolTipText( MessageText.getString( "long.press.cancel.tt" ));
			
		    cancel.addListener(
				SWT.MouseDown,
				new Listener()
				{
					boolean 	mouseDown 	= false;
					TimerEvent	timerEvent 	= null;
					
					public void 
					handleEvent(
						Event event )
					{
						if ( event.button != 1 ){
							
							return;
						}
						
						if (timerEvent == null) {
							timerEvent = SimpleTimer.addEvent("MouseHold",
									SystemTime.getOffsetTime(1000), te -> {
										timerEvent = null;
										if (!mouseDown) {
											return;
										}
	
										Utils.execSWTThread(() -> {
											if (!mouseDown) {
												return;
											}
	
											if ( event.display.getCursorControl() != cancel ) {
												return;
											}
	
											mouseDown = false;
	
											user_hit_escape = true;
											
											shell.dispose();
										});
									});
						}
	
						mouseDown = true;
					}
				});
	    }
	    
	    if ( text_entry_text != null ){
	    	
	    	text_entry_text.addFocusListener(
		    	new FocusAdapter(){
		    		@Override
		    		public void focusLost(FocusEvent e){
					
		    			checkText();
		    		}
				});
	    	
	    	checkText();
	    }
	    
	    shell.setDefaultButton(ok);

		shell.addListener(SWT.Traverse, new Listener() {
			@Override
			public void handleEvent(Event e) {
				if ( e.character == SWT.ESC){
					user_hit_escape = true;
					SimpleTextEntryWindow.this.recordUserAbort();
					shell.dispose();
				}
			}
		});

		shell.addListener(SWT.Dispose, event -> {
			if (!isResultRecorded()) {
				recordUserAbort();
			}
			Utils.execSWTThreadLater(0, this::triggerReceiverListener);
		});
		
	    shell.pack();
	    if (text_entry_text != null)
	    	DragDropUtils.createURLDropTarget(shell, text_entry_text);

	    	// don't shrink this control otherwise the manual speed entry for up/down speed on
	    	// the transfers bar doesn't work as parent shell small...

	    boolean centre = true;
	    
		if ( loc_size_config_key != null ){
			
			if ( Utils.linkShellMetricsToConfig( shell, loc_size_config_key )){
				
				centre = false;
			}
		}
		if ( centre ){
			Utils.centreWindow(shell,false);
		}
		
	    shell.open();
	  }

	private void
	checkText()
	{
		if ( detect_urls ){
			String url = UrlUtils.parseTextForURL( text_entry_text.getText(), true );
			
			if ( url != null ){
				
				link_label.setText( url );
				
				LinkLabel.makeLinkedLabel( link_label, url );
				
			}else{
				link_label.setText( "" );
				
				LinkLabel.removeLinkedLabel( link_label );
			}
		}
	}
	
  @Override
  public void setTextLimit(int limit) {
  	textLimit = limit;
  	Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				if (text_entry_combo != null && !text_entry_combo.isDisposed()) {
					text_entry_combo.setTextLimit(textLimit);
				}
				if (text_entry_text != null && !text_entry_text.isDisposed()) {
					text_entry_text.setTextLimit(textLimit);
				}
			}
		});
  }
  
  public void
  setResizeable(
		boolean b )
  {
	  resizeable = b;
  }

  public void
  setRememberLocationSize(
		 String		config_key )
  {
	  loc_size_config_key = config_key;
	  resizeable = true;
  }
  
  public void
  setDetectURLs(
		 boolean	b )
  {
	  detect_urls = b;
  }
  
  public void
  setParentShell(
	Shell		shell )
  {
	  parent_shell = shell;
  }
  
  @Override
  public void setEnableSpecialEscapeHandling(boolean b){
	special_escape_handling = b;
  }
  
  @Override
  public boolean 
  userHitEscape(){
	 return( user_hit_escape );
  }
}
