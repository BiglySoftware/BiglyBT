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

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.Debug;
import com.biglybt.ui.swt.components.shell.ShellFactory;
import com.biglybt.ui.swt.mainwindow.SWTThread;

public class TextViewerWindow {
  private Shell shell;
  private Text txtInfo;
  private  Button ok;

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
	Shell parent_shell, String sTitleID, String sMessageID, String sText, boolean modal, boolean defer_modal )
  {
    if ( modal ){

    	if ( parent_shell == null ){

    		shell = ShellFactory.createMainShell( SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL | SWT.RESIZE | SWT.MAX );
    	}else{

    		shell = ShellFactory.createShell( parent_shell, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL | SWT.RESIZE | SWT.MAX );

    	}
    }else{

    	if ( parent_shell == null ){

    		shell = ShellFactory.createMainShell(SWT.DIALOG_TRIM | SWT.RESIZE | SWT.MAX );

    	}else{

    		shell = ShellFactory.createShell( parent_shell, SWT.DIALOG_TRIM | SWT.RESIZE | SWT.MAX );
    	}
    }

    if (sTitleID != null) shell.setText(MessageText.keyExists(sTitleID)?MessageText.getString(sTitleID):sTitleID);

    Utils.setShellIcon(shell);

    GridLayout layout = new GridLayout();
    layout.numColumns = 2;
    shell.setLayout(layout);

    Label label = new Label(shell, SWT.NONE);
    if (sMessageID != null) label.setText(MessageText.keyExists(sMessageID)?MessageText.getString(sMessageID):sMessageID);
    GridData gridData = new GridData(  GridData.FILL_HORIZONTAL );
    gridData.widthHint = 200;
    gridData.horizontalSpan = 2;
    Utils.setLayoutData(label, gridData);

    txtInfo = new Text(shell, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL);
    gridData = new GridData(  GridData.FILL_BOTH );
    gridData.widthHint = 600;
    gridData.heightHint = 400;
    gridData.horizontalSpan = 2;
    Utils.setLayoutData(txtInfo, gridData);
    txtInfo.setText(sText);

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

    label = new Label(shell, SWT.NONE);
    gridData = new GridData( GridData.FILL_HORIZONTAL );
    Utils.setLayoutData(label, gridData);

    ok = new Button(shell, SWT.PUSH);
    ok.setText(MessageText.getString("Button.ok"));
    gridData = new GridData();
    gridData.widthHint = 70;
    Utils.setLayoutData(ok, gridData);
    shell.setDefaultButton(ok);
    ok.addListener(SWT.Selection, new Listener() {
      @Override
      public void handleEvent(Event event) {
        try {
        	shell.dispose();
        }
        catch (Exception e) {
        	Debug.printStackTrace( e );
        }
      }
    });

	shell.addListener(SWT.Traverse, new Listener() {
		@Override
		public void handleEvent(Event e) {
			if ( e.character == SWT.ESC){
				if ( ok.isEnabled()){
					shell.dispose();
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
				for ( TextViewerWindowListener l: listeners ){

					l.closed();
				}
			}
		});

    shell.pack();
	Utils.centreWindow( shell );
    shell.open();

    if ( modal && !defer_modal ){
    	goModal();
    }
  }

  public void
  goModal()
  {
	    Display display = Utils.getDisplay();

	  	while (!shell.isDisposed()){
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

	  txtInfo.setSelection( txtInfo.getTextLimit());
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
	  txtInfo.setText( text);

	  txtInfo.setSelection( txtInfo.getTextLimit());
  }

  public void
  setEditable(
	boolean	editable )
  {
	  txtInfo.setEditable( editable );
  }

  public void
  setOKEnabled(
	boolean	enabled )
  {
	  ok.setEnabled( enabled );
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
	  if ( !shell.isDisposed()){

		  shell.dispose();
	  }
  }
  public interface
  TextViewerWindowListener
  {
	  public void
	  closed();
  }
}
