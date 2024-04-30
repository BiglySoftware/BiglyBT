/* 
 * Copyright (C) Bigly Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA
 */

package com.biglybt.ui.swt;


import java.util.Calendar;
import java.util.GregorianCalendar;

import org.eclipse.swt.SWT;

import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.*;
import com.biglybt.core.internat.MessageText;
import com.biglybt.ui.swt.components.shell.ShellFactory;

public class DateWindow {
  private Shell 	shell;

  public
  DateWindow(
		  String 		sTitleID,
		  long			current,
		  DateReceiver	receiver )
  {
   	shell = ShellFactory.createMainShell(SWT.DIALOG_TRIM | SWT.RESIZE | SWT.MAX );

    if (sTitleID != null) shell.setText(MessageText.keyExists(sTitleID)?MessageText.getString(sTitleID):sTitleID);

    Utils.setShellIcon(shell);

    GridLayout layout = new GridLayout();
    layout.numColumns = 1;
    shell.setLayout(layout);

    DateTime calendar = new DateTime(shell, SWT.CALENDAR | SWT.LONG );
    GridData gridData = new GridData(GridData.FILL_BOTH);
    calendar.setLayoutData(gridData);

    if ( current >= 0 ){
    	GregorianCalendar cal = new GregorianCalendar();
    	cal.setTimeInMillis( current );
    	
    	calendar.setDate(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
    }
    
    Composite panel = new Composite(shell, SWT.NULL);
    final RowLayout rLayout = new RowLayout();
    rLayout.marginTop = 0;
    rLayout.marginLeft = 0;
    rLayout.marginBottom = 0;
    rLayout.marginRight = 0;
   	rLayout.fill = true;
  
    rLayout.spacing = Utils.BUTTON_MARGIN;
    panel.setLayout(rLayout);
    gridData = new GridData();
    gridData.horizontalAlignment = SWT.END;
    panel.setLayoutData(gridData);

    Button[] buttons = Utils.createOKCancelButtons(panel);

    Button ok 		= buttons[0];
    Button cancel 	= buttons[1];

    ok.addListener(SWT.Selection, new Listener() {
        @Override
       public void handleEvent(Event event) {
        	GregorianCalendar cal = new GregorianCalendar( calendar.getYear(), calendar.getMonth(), calendar.getDay());
       
        	receiver.dateSelected(cal.getTimeInMillis());
        	
            shell.dispose();
       }
     });
    
    cancel.addListener(SWT.Selection, new Listener() {
         @Override
        public void handleEvent(Event event) {
             shell.dispose();
        }
      });

    
    shell.setDefaultButton( ok );
    
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
				
			}
		});

    shell.pack();
	Utils.centreWindow( shell );
    shell.open();
  }
  
  public interface
  DateReceiver
  {
	  public void
	  dateSelected(
		long	millis );
  }
}
