/*
 * Created on 02-Oct-2005
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

package com.biglybt.ui.swt;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.UrlUtils;
import com.biglybt.ui.swt.components.BufferedLabel;
import com.biglybt.ui.swt.components.shell.ShellFactory;


public class
PropertiesWindow
{
	private final Shell		shell;

	private Map<String,BufferedLabel>	field_map = new HashMap<>();

	public
	PropertiesWindow(
		String					object_name,
		java.util.List<String>	keys,
		java.util.List<String>	values )
	{
		this( object_name, keys.toArray(new String[0]), values.toArray( new String[0]));
	}
	
	public
	PropertiesWindow(
		String		object_name,
		String[]	keys,
		String[]	values )
	{
		shell = ShellFactory.createMainShell(SWT.TITLE | SWT.CLOSE |SWT.RESIZE );

		shell.setText( MessageText.getString( "props.window.title", new String[]{ object_name }));

		Utils.setShellIcon(shell);

	    GridLayout layout = new GridLayout();
	    layout.numColumns = 3;
	    shell.setLayout(layout);

	    final ScrolledComposite scrollable = new ScrolledComposite(shell, SWT.V_SCROLL | SWT.H_SCROLL );
	    GridData gridData = new GridData(SWT.FILL, SWT.FILL, true, true);
	    gridData.horizontalSpan = 3;

		scrollable.setLayoutData(gridData);

		/*
		 * Main content composite where panels will be created
		 */
		final Composite main = new Composite(scrollable, SWT.NONE);

		layout = new GridLayout();
		layout.marginHeight = 0;
		layout.marginWidth = 0;
		//layout.verticalSpacing = 0;
		layout.numColumns = 2;
		main.setLayout(layout);

		scrollable.setContent(main);
		scrollable.setExpandVertical(true);
		scrollable.setExpandHorizontal(true);

		/*
		 * Re-adjust scrollbar setting when the window resizes
		 */
		scrollable.addControlListener(new ControlAdapter() {
			@Override
			public void controlResized(ControlEvent e) {
				Rectangle r = scrollable.getClientArea();
				scrollable.setMinSize(main.computeSize(r.width, SWT.DEFAULT ));
			}
		});

	    gridData = new GridData(GridData.FILL_BOTH);
	    gridData.horizontalSpan = 3;
		main.setLayoutData(gridData);

	    for (int i=0;i<keys.length;i++){

	    	if ( keys[i] == null || values[i] == null ){

	    		continue;
	    	}

		    BufferedLabel	msg_label = new BufferedLabel(main, SWT.NULL);
		    String msg;
		    String key = keys[i];
		    if ( key.length() == 0 ){
		    	msg = "";
		    }else if ( key.startsWith( "!" ) && key.endsWith( "!" )){
		    	msg = key.substring(1, key.length()-1 );
		    }else{
		    	msg = MessageText.getString( key );
		    }

		    String value = values[i];

		    	// hack to allow key values on their own

		    if ( value.equals( "<null>" )){

		    	msg_label.setText( msg );

		    	value = "";
		    }else{

		    	msg_label.setText( msg.length()==0?"":(msg + ":" ));
		    }

		    gridData = new GridData();
		    gridData.verticalAlignment = GridData.VERTICAL_ALIGN_FILL;
			msg_label.setLayoutData(gridData);

		    BufferedLabel	val_label = new BufferedLabel(main,SWT.WRAP);
		    
		    URL url = UrlUtils.getRawURL( value );
		    
		    if ( url == null ){
		    
		    	val_label.setText( value );
		    	
		    }else{
		    
		    	val_label.setText( UrlUtils.getFriendlyName( url, value ));
		    	
			   	val_label.setLink( url.toExternalForm());
		    }
		    
		    gridData = new GridData(GridData.FILL_HORIZONTAL);
		    gridData.horizontalIndent = 6;
			val_label.setLayoutData(gridData);

		    field_map.put( key, val_label );
	    }

			// separator

		Label labelSeparator = new Label(shell,SWT.SEPARATOR | SWT.HORIZONTAL);
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		gridData.horizontalSpan = 3;
		labelSeparator.setLayoutData(gridData);

			// buttons

		new Label(shell,SWT.NULL);

		Button bOk = new Button(shell,SWT.PUSH);
	 	Messages.setLanguageText(bOk, "Button.ok");
	 	gridData = new GridData(GridData.FILL_HORIZONTAL | GridData.HORIZONTAL_ALIGN_END | GridData.HORIZONTAL_ALIGN_FILL);
	 	gridData.grabExcessHorizontalSpace = true;
	 	gridData.widthHint = 70;
		bOk.setLayoutData(gridData);
		bOk.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event e) {
				close();
			}
		});

	 	Button bCancel = new Button(shell,SWT.PUSH);
	 	Messages.setLanguageText(bCancel, "Button.cancel");
	 	gridData = new GridData(GridData.HORIZONTAL_ALIGN_END);
	 	gridData.grabExcessHorizontalSpace = false;
	 	gridData.widthHint = 70;
		bCancel.setLayoutData(gridData);
	 	bCancel.addListener(SWT.Selection,new Listener() {
	 		@Override
		  public void handleEvent(Event e) {
		 		close();
	   		}
	 	});

	 	shell.setDefaultButton( bOk );

	 	shell.addListener(SWT.Traverse, new Listener() {
			@Override
			public void handleEvent(Event e) {
				if ( e.character == SWT.ESC){
					close();
				}
			}
		});

	 	if (!Utils.linkShellMetricsToConfig(shell, "PropWin")) {
	  	 	int	shell_width = 400;

	  	 	int	main_height = main.computeSize(shell_width, SWT.DEFAULT).y;

	  	 	main_height = Math.max( main_height, 250 );

	  	 	main_height = Math.min( main_height, 500 );

	  	 	int shell_height = main_height + 50;

	  	 	shell.setSize( shell_width, shell_height );
	 	}

		Utils.centreWindow( shell );

		shell.open();
	}

	public void
	updateProperty(
		final String		key,
		final String		value )
	{
		Utils.execSWTThread(
			new Runnable() {

				@Override
				public void run() {

					BufferedLabel label = field_map.get( key );

					if ( label != null && !label.isDisposed()){

						label.setText( value );
					}
				}
			});
	}

	protected void
	close()
	{
		if ( !shell.isDisposed()){

			shell.dispose();
		}
	}
}
