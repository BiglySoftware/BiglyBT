/*
 * Created on 2 feb. 2004
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

import java.util.*;
import java.util.List;
import java.util.function.Consumer;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;
import com.biglybt.ui.swt.components.shell.ShellFactory;

public class TextViewerWindow {
	
  private boolean reuseWindow;
  
  private Consumer<TextViewerWindow> refresher;
  
  private boolean modal;
  private boolean defer_modal;
  
  private final Shell shell;
  private final Text txtInfo;
  
  private final Composite buttonArea;
  
  private Button refresh;
  private Button ok;
  private Button cancel;
  
  private boolean	cancel_enabled;
  
  private Font	np_font;
  
  private boolean ok_pressed;
  
  
  private List<TextViewerWindowListener> listeners = new ArrayList<>();

  public
  TextViewerWindow(
		 String sTitleID, String sMessageID, String sText )
  {
	  this( sTitleID, sMessageID, sText, true );
  }

  public
  TextViewerWindow(
		 String sTitleID, String sMessageID, String sText, boolean modal )
  {
	this( sTitleID, sMessageID, sText, modal, false );
  }

  public
  TextViewerWindow(
		 String sTitleID, String sMessageID, String sText, boolean modal, boolean defer_modal )
  {
	  this( null, sTitleID, sMessageID, sText, modal, defer_modal );
  }

  public
  TextViewerWindow(
	Shell parent_shell, String sTitleID, String sMessageID, String sText, boolean _modal, boolean _defer_modal )
  {
	  this( parent_shell, sTitleID, sMessageID, sText, null, _modal, _defer_modal );
  }
  
  public
  TextViewerWindow(
	Shell parent_shell, String sTitleID, String sMessageID, String sText, Consumer<TextViewerWindow> _refresher, boolean _modal, boolean _defer_modal )
  {
	refresher = _refresher;
	  
	modal = _modal;
	defer_modal = _defer_modal;
	
    if ( modal ){

    	if ( parent_shell == null ){

    		shell = ShellFactory.createMainShell( SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL | SWT.RESIZE | SWT.MIN | SWT.MAX );
    	}else{

    		shell = ShellFactory.createShell( parent_shell, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL | SWT.RESIZE | SWT.MIN | SWT.MAX );

    	}
    }else{

    	if ( parent_shell == null ){

    		shell = ShellFactory.createMainShell(SWT.DIALOG_TRIM | SWT.RESIZE | SWT.MIN | SWT.MAX );

    	}else{

    		shell = ShellFactory.createShell( parent_shell, SWT.DIALOG_TRIM | SWT.RESIZE | SWT.MIN | SWT.MAX );
    	}
    }

    if (sTitleID != null) shell.setText(MessageText.keyExists(sTitleID)?MessageText.getString(sTitleID):sTitleID);

    Utils.setShellIcon(shell);

    GridLayout layout = new GridLayout();
    layout.numColumns = 3;
    shell.setLayout(layout);

    Label label = new Label(shell, SWT.NONE);
    if (sMessageID != null) label.setText(MessageText.keyExists(sMessageID)?MessageText.getString(sMessageID):sMessageID);
    GridData gridData = new GridData(  GridData.FILL_HORIZONTAL );
    gridData.widthHint = 200;
    gridData.horizontalSpan = 3;
    label.setLayoutData(gridData);

    txtInfo = new Text(shell, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL | SWT.WRAP);
    gridData = new GridData(  GridData.FILL_BOTH );
    gridData.widthHint = 600;
    gridData.heightHint = 400;
    gridData.horizontalSpan = 3;
    txtInfo.setLayoutData(gridData);
    txtInfo.setText(sText);
    
    np_font = new Font(shell.getDisplay(), "Courier", txtInfo.getFont().getFontData()[0].getHeight(), SWT.NORMAL);
    
    txtInfo.addKeyListener(new KeyListener() {

			@Override
			public void keyPressed(KeyEvent e) {
				int key = e.character;
				if (key <= 26 && key > 0) {
					key += 'a' - 1;
				}

				if (key == 'a' && e.stateMask == SWT.MOD1) {
					if (txtInfo != null) {
						txtInfo.selectAll();
					}
				}
			}

			@Override
			public void keyReleased(KeyEvent e) {
			}

    });

    buttonArea = new Composite(shell, SWT.NULL );
    buttonArea.setLayout( new GridLayout(  3, false ));
    gridData = new GridData(  GridData.FILL_HORIZONTAL );
    gridData.horizontalSpan = 3;
    buttonArea.setLayoutData(gridData);

    buildButtons();
    
	shell.addListener(SWT.Traverse, new Listener() {
		@Override
		public void handleEvent(Event e) {
			if ( e.character == SWT.ESC){
				if ( ok.isEnabled()){
					
					closeWindow();
				}
			}
		}
	});

	shell.addDisposeListener(
		new DisposeListener()
		{
			@Override
			public void
			widgetDisposed(
				DisposeEvent arg0)
			{
				if ( reuseWindow ){
					Debug.out( "What?" );
				}
				if ( np_font != null ){
					np_font.dispose();
					np_font = null;
				}
				
				for ( TextViewerWindowListener l: listeners ){

					l.closed();
				}
			}
		});

	boolean	pack = true;
	
	if ( sTitleID != null ){
		
		if ( Utils.linkShellMetricsToConfig( shell, "TextViewerWindow:" + sTitleID )){
	
			pack = false;
		}
	}
	
	if ( pack ){
		
		shell.pack();
		
		Utils.centreWindow( shell );
	}
	
    shell.open();

    if ( modal && !defer_modal ){
    	goModal();
    }
  }

  public void
  setReuseWindow()
  {
	  reuseWindow = true;
	  
	  shell.addListener (
			SWT.Close, new Listener () 
			{
				public void handleEvent (Event event) {
					event.doit = false;
					closeWindow();
				}
			});
  }
  
  public void
  reset()
  {
	  listeners.clear();
	  
	  txtInfo.setText( "" );
	  shell.setVisible( true );  

	  Utils.centreWindow( shell );
	  
	    if ( modal && !defer_modal ){
	    	goModal();
	    }
  }
  
  private void
  buildButtons()
  {
	  Utils.disposeComposite( buttonArea, false );

	  Label label = new Label(buttonArea, SWT.NONE);
	  GridData gridData = new GridData( GridData.FILL_HORIZONTAL );
	  label.setLayoutData(gridData);

	  if ( refresher != null ){
		  refresh = new Button(buttonArea, SWT.PUSH);
		  refresh.setText(MessageText.getString("upnp.refresh.button"));
		  refresh.addListener(SWT.Selection, new Listener() {
			  @Override
			  public void handleEvent(Event event) {
				  refresher.accept(TextViewerWindow.this);
			  }
		  });
	  }
	  
	  if ( cancel_enabled ){
		  if (Constants.isOSX) {
			  cancel = new Button(buttonArea, SWT.PUSH);
			  ok = new Button(buttonArea, SWT.PUSH);
		  }else{
			  ok = new Button(buttonArea, SWT.PUSH);
			  cancel = new Button(buttonArea, SWT.PUSH);
		  }
		  cancel.setText(MessageText.getString("Button.cancel"));
	  }else{
		  cancel = null;
		  ok = new Button(buttonArea, SWT.PUSH);
	  }
	  ok.setText(MessageText.getString("Button.ok"));

	  Utils.makeButtonsEqualWidth( Arrays.asList( refresh, ok, cancel ));

	  shell.setDefaultButton(ok);
	  
	  ok.addListener(SWT.Selection, new Listener() {
		  @Override
		  public void handleEvent(Event event) {
			  try {
				  ok_pressed = true;

				  closeWindow();
			  }
			  catch (Exception e) {
				  Debug.printStackTrace( e );
			  }
		  }
	  });

	  if ( cancel != null ){
		  cancel.addListener(SWT.Selection, new Listener() {
			  @Override
			  public void handleEvent(Event event) {
				  try {
					  closeWindow();
				  }
				  catch (Exception e) {
					  Debug.printStackTrace( e );
				  }
			  }
		  });
	  }
	  
	  buttonArea.layout( true, true );
  }

  private void 
  closeWindow()
  {
	  if ( reuseWindow ){
		  shell.setVisible( false );
		  
			for ( TextViewerWindowListener l: listeners ){

				l.closed();
			}
	  }else{
		  if ( !shell.isDisposed()){
		  
			  shell.dispose();
		  }
	  }
  }
  
  public void
  goModal()
  {
	    Display display = Utils.getDisplay();

	  	while ( (!shell.isDisposed()) && shell.isVisible()){
    		if (!display.readAndDispatch()) display.sleep();
	  	}
  }

  public void
  append(
	String	str )
  {
	  txtInfo.setText( txtInfo.getText() + str );

	  txtInfo.setSelection( txtInfo.getTextLimit());
  }

  public void
  append2(
	String	str )
  {
	  txtInfo.append( str );

	  if ( str.contains( "\n" )){
	  
		  	// only scroll if the newly added text contains a new-line
		  	// otherwise things get twitchy
		  
		  txtInfo.setSelection( txtInfo.getTextLimit());
	  }
  }

  public boolean
  getOKPressed()
  {
	  return( ok_pressed );
  }
  
  public String
  getText()
  {
	  return( txtInfo.getText());
  }

  public void
  setText(
	 String	text )
  {
	  setText( text, true );
  }
  
  public void
  setText(
	 String		text,
	 boolean	scrollToBottom )
  {
	  txtInfo.setText( text);

	  if ( scrollToBottom ){
		  
		  txtInfo.setSelection( txtInfo.getTextLimit());
	  }
  }

  public void
  setEditable(
	boolean	editable )
  {
	  txtInfo.setEditable( editable );
	 
	  if ( editable ){
		  
		  cancel_enabled = true;
	  }
	  
	  buildButtons();
  }

  public void
  setOKEnabled(
	boolean	enabled )
  {
	  ok.setEnabled( enabled );
  }
  
  public void
  setCancelEnabled(
	boolean	enabled )
  {
	  if ( cancel != null ){
		  cancel.setEnabled( enabled );
	  }
  }

  public void
  setNonProportionalFont()
  {
	  txtInfo.setFont( np_font );
  }
  
  public void
  addListener(
	 TextViewerWindowListener		l )
  {
	  listeners.add( l );
  }

  public boolean
  isDisposed()
  {
	  return( shell.isDisposed());
  }

  public void
  close()
  {
	 closeWindow();
  }
  public interface
  TextViewerWindowListener
  {
	  public void
	  closed();
  }
}
