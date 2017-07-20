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

import java.io.File;
import java.io.FileInputStream;
import java.util.*;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.Debug;
import com.biglybt.ui.swt.components.shell.ShellFactory;

public class ImageViewerWindow {
  private Shell 	shell;
  private Button 	ok;
  private Image		image;

  private List<TextViewerWindowListener> listeners = new ArrayList<>();

  public
  ImageViewerWindow(
		 String sTitleID, String sMessageID, File image_file )
  {
	  this( sTitleID, sMessageID, image_file, null );
  }

  public
  ImageViewerWindow(
		 String sTitleID, String sMessageID, Image img )
  {
	  this( sTitleID, sMessageID, null, img );
  }

  private
  ImageViewerWindow(
		 String sTitleID, String sMessageID, File image_file, Image img )
  {
   	shell = ShellFactory.createMainShell(SWT.DIALOG_TRIM | SWT.RESIZE | SWT.MAX );

    if (sTitleID != null) shell.setText(MessageText.keyExists(sTitleID)?MessageText.getString(sTitleID):sTitleID);

    Utils.setShellIcon(shell);

    GridLayout layout = new GridLayout();
    layout.numColumns = 2;
    shell.setLayout(layout);

    Label label = new Label(shell, SWT.NONE);
    if (sMessageID != null) label.setText(MessageText.keyExists(sMessageID)?MessageText.getString(sMessageID):sMessageID);
    GridData gridData = new GridData(  GridData.FILL_HORIZONTAL );
   // gridData.widthHint = 200;
    gridData.horizontalSpan = 2;
    Utils.setLayoutData(label, gridData);

    final ScrolledComposite sc = new ScrolledComposite(shell, SWT.H_SCROLL | SWT.V_SCROLL);
    sc.setExpandHorizontal(true);
    sc.setExpandVertical(true);
    gridData = new GridData(  GridData.FILL_BOTH );
    gridData.widthHint = 500;
    gridData.heightHint = 400;
    gridData.horizontalSpan = 2;
    Utils.setLayoutData(sc, gridData);

    layout = new GridLayout();
	layout.horizontalSpacing = 0;
	layout.verticalSpacing = 0;
	layout.marginHeight = 0;
	layout.marginWidth = 0;
	sc.setLayout(layout);

	final Composite img_comp = new Composite( sc, SWT.NONE );
	img_comp.setLayout( new GridLayout());

    Label img_label = new Label(img_comp, SWT.BORDER );
    img_label.setAlignment( SWT.CENTER );
    gridData = new GridData(  GridData.FILL_BOTH );
    Utils.setLayoutData(img_label, gridData);

    sc.setContent( img_comp );
    sc.addControlListener(new ControlAdapter() {
		@Override
		public void controlResized(ControlEvent e) {
			sc.setMinSize(img_comp.computeSize(SWT.DEFAULT, SWT.DEFAULT ));
		}
	});

    if ( img == null ){

	    try{
	    	FileInputStream is = new FileInputStream( image_file );

	    	try{
	    		image = new Image( shell.getDisplay(), is );

	    	}finally{

	    		is.close();
	    	}
	    }catch( Throwable e ){
	    	e.printStackTrace();

	    }
    }else{

    	image = img;
    }

    if ( image != null ){

    	img_label.setImage( image );

    	img_label.addDisposeListener(
    		new DisposeListener()
    		{
				@Override
				public void
				widgetDisposed(DisposeEvent e)
				{
					image.dispose();
				}
			});
    }

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
